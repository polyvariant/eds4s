/*
 * Copyright 2024 Polyvariant
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

package eds4s

import cats.Monad
import cats.effect.Resource
import cats.syntax.all.*

/** Main algebra for calendar operations using tagless final encoding */
trait CalendarClient[F[_]] {
  def listCalendars: F[List[CalendarInfo]]
  def getCalendar(uid: String): F[Option[CalendarInfo]]
  def createCalendar(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String] = None
  ): F[CalendarInfo]
  def deleteCalendar(uid: String): F[Unit]
  def getEvents(
      calendarUid: String,
      query: EventQuery = EventQuery.all
  ): F[List[Event]]
  def getEvent(calendarUid: String, eventId: EventId): F[Option[Event]]
  def createEvent(calendarUid: String, event: EventData): F[EventId]
  def modifyEvent(
      calendarUid: String,
      event: Event,
      modType: ModificationType = ModificationType.This
  ): F[Unit]
  def deleteEvent(
      calendarUid: String,
      eventId: EventId,
      modType: ModificationType = ModificationType.This
  ): F[Unit]

  /** Close a previously opened calendar and release its resources.
    *
    * Note: Calendars are automatically closed when the DBusCalendarClient
    * resource is finalized, but this allows explicit cleanup.
    *
    * @param calendarUid
    *   The calendar UID to close
    */
  def closeCalendar(calendarUid: String): F[Unit]
}

object CalendarClient {
  def apply[F[_]](implicit ev: CalendarClient[F]): CalendarClient[F] = ev
}

/** Reference to a specific calendar for fluent API */
final case class CalendarRef[F[_]: Monad](
    uid: String,
    client: CalendarClient[F]
) {
  def info: F[Option[CalendarInfo]] = client.getCalendar(uid)
  def events: F[List[Event]] = client.getEvents(uid)
  def events(query: EventQuery): F[List[Event]] = client.getEvents(uid, query)
  def event(eventId: EventId): F[Option[Event]] = client.getEvent(uid, eventId)
  def create(event: EventData): F[EventId] = client.createEvent(uid, event)
  def modify(event: Event): F[Unit] = client.modifyEvent(uid, event)
  def delete(eventId: EventId): F[Unit] = client.deleteEvent(uid, eventId)
  def delete: F[Unit] = client.deleteCalendar(uid)
}

/** Extension methods for CalendarClient */
extension [F[_]: Monad](client: CalendarClient[F]) {
  def calendar(uid: String): CalendarRef[F] = CalendarRef(uid, client)
  def findCalendarsByName(name: String): F[List[CalendarInfo]] =
    client.listCalendars.map(
      _.filter(_.name.toLowerCase.contains(name.toLowerCase))
    )
  def calendarExists(uid: String): F[Boolean] =
    client.getCalendar(uid).map(_.isDefined)
}
