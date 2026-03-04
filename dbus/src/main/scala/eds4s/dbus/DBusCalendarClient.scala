/*
 * Copyright 2026 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// EDS4S - Evolution Data Server for Scala
// Copyright (C) 2024 EDS4S Contributors
// SPDX-License-Identifier: Apache-2.0

package eds4s.dbus

import cats.effect.Sync
import cats.effect.Resource
import cats.effect.Async
import cats.effect.implicits._
import cats.syntax.all.*
import eds4s.*
import eds4s.EdsError.*

/** Live implementation of CalendarClient using DBus to communicate with EDS.
  *
  * Requires Async[F] for blocking DBus operations. Async provides Concurrent[F]
  * which enables parallel event parsing via parTraverse.
  */
final class DBusCalendarClient[F[_]: Async] private (
    sourceRegistry: SourceRegistryClient[F],
    calendarFactory: CalendarFactoryClient[F],
    calendarOpsClient: CalendarOperationsClient[F],
    icalConverter: IcalConverter[F]
) extends CalendarClient[F] {

  override def listCalendars: F[List[CalendarInfo]] =
    sourceRegistry.listSources

  override def getCalendar(uid: String): F[Option[CalendarInfo]] =
    listCalendars.map(_.find(_.uid == uid))

  override def createCalendar(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String]
  ): F[CalendarInfo] =
    for uid <- sourceRegistry.createSource(name, backend, calendarType)
    // EDS may not support color directly, so we create the calendar first
    // and return the info
    info <- getCalendar(uid).flatMap {
      case Some(info) => Async[F].pure(info.copy(color = color))
      case None       => Async[F].raiseError(CalendarNotFound(uid))
    } yield info

  override def deleteCalendar(uid: String): F[Unit] =
    sourceRegistry.removeSource(uid)

  override def getEvents(
      calendarUid: String,
      query: EventQuery
  ): F[List[Event]] =
    for proxy <- calendarFactory.openCalendar(calendarUid)
    sexp = EventQuery
      .render(query)
      .getOrElse("#t") // "#t" means "true" - match all events
    icsList <- calendarOpsClient.getObjectList(proxy, sexp)
    // Use parTraverse for parallel event parsing (Async provides Concurrent)
    events <- icsList.parTraverse(icalConverter.parseEvent(_)).handleErrorWith {
      e =>
        Async[F].raiseError(
          InvalidIcsFormat(s"Failed to parse events: ${e.getMessage}", Some(e))
        )
    } yield events

  override def getEvent(
      calendarUid: String,
      eventId: EventId
  ): F[Option[Event]] =
    for proxy <- calendarFactory.openCalendar(calendarUid)
    ics <- calendarOpsClient
      .getObject(proxy, eventId.value)
      .map(Some(_))
      .handleErrorWith { _ =>
        // If event not found, return None
        Sync[F].pure(None: Option[String])
      }
    event <- ics match {
      case Some(icsStr) => icalConverter.parseEvent(icsStr).map(Some(_))
      case None         => Sync[F].pure(None)
    } yield event

  override def createEvent(
      calendarUid: String,
      eventData: EventData
  ): F[EventId] =
    for proxy <- calendarFactory.openCalendar(calendarUid)
    ics <- icalConverter.renderEvent(eventData)
    uids <- calendarOpsClient.createObjects(proxy, List(ics))
    uid <- uids.headOption match {
      case Some(uid) =>
        Sync[F].pure(EventId(uid.stripPrefix("[").stripSuffix("]")))
      case None =>
        Sync[F].raiseError(
          CalendarOperationFailed("createEvent", "No UID returned")
        )
    } yield uid

  override def modifyEvent(
      calendarUid: String,
      event: Event,
      modType: ModificationType
  ): F[Unit] =
    for proxy <- calendarFactory.openCalendar(calendarUid)
    ics <- icalConverter.renderExistingEvent(event)
    // ModifyObjects is not in CalendarOperationsClient yet, but we can use createObjects
    // which typically replaces existing events with same UID
    _ <- calendarOpsClient.createObjects(proxy, List(ics))
    yield ()

  override def deleteEvent(
      calendarUid: String,
      eventId: EventId,
      modType: ModificationType
  ): F[Unit] =
    for proxy <- calendarFactory.openCalendar(calendarUid)
    _ <- calendarOpsClient.removeObjects(proxy, List(eventId.value), modType)
    yield ()

  override def closeCalendar(calendarUid: String): F[Unit] =
    calendarFactory.closeCalendar(calendarUid)
}

object DBusCalendarClient {

  /** Create a DBusCalendarClient using a DBus connection resource */
  def resource[F[_]: Async]: Resource[F, CalendarClient[F]] =
    for conn <- DBusConnection.resource[F]
    client <- Resource.eval(createClient(conn))
    yield client

  /** Create a DBusCalendarClient from an existing DBus connection */
  def apply[F[_]: Async](conn: DBusConnection[F]): F[CalendarClient[F]] =
    createClient(conn)

  private def createClient[F[_]: Async](
      conn: DBusConnection[F]
  ): F[CalendarClient[F]] =
    for sourceRegistry <- Sync[F].pure(SourceRegistryClient[F](conn))
    calendarOpsClient <- CalendarOperationsClient[F](conn)
    calendarFactory <- CalendarFactoryClient[F](conn, calendarOpsClient)
    icalConverter <- Sync[F].pure(IcalConverter[F])
    yield new DBusCalendarClient[F](
      sourceRegistry,
      calendarFactory,
      calendarOpsClient,
      icalConverter
    )
}
