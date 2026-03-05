/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus

import cats.effect.IO
import cats.effect.Resource
import eds4s.*

/** Mock CalendarClient for testing on CI where DBus is not available. All
  * methods return stub data or succeed with no-op.
  */
private[dbus] object MockCalendarClient extends CalendarClient[IO]:
  override def listCalendars: IO[List[CalendarInfo]] = IO.pure(Nil)

  override def getCalendar(uid: String): IO[Option[CalendarInfo]] =
    IO.pure(None)

  override def createCalendar(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String]
  ): IO[CalendarInfo] = IO.pure(
    CalendarInfo(
      uid = "mock-calendar-uid",
      name = name,
      backend = backend,
      calendarType = calendarType,
      color = color,
      visible = true,
      writable = true,
      sourceUri = None
    )
  )

  override def deleteCalendar(uid: String): IO[Unit] = IO.unit

  override def getEvents(
      calendarUid: String,
      query: EventQuery
  ): IO[List[Event]] = IO.pure(Nil)

  override def getEvent(
      calendarUid: String,
      eventId: EventId
  ): IO[Option[Event]] = IO.pure(None)

  override def createEvent(calendarUid: String, event: EventData): IO[EventId] =
    IO.pure(EventId("mock-event-uid"))

  override def modifyEvent(
      calendarUid: String,
      event: Event,
      modType: ModificationType
  ): IO[Unit] = IO.unit

  override def deleteEvent(
      calendarUid: String,
      eventId: EventId,
      modType: ModificationType
  ): IO[Unit] = IO.unit

  override def closeCalendar(calendarUid: String): IO[Unit] = IO.unit
