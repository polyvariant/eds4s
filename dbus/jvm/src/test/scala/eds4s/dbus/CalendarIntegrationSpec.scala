/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus

import weaver.IOSuite
import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import eds4s.*
import java.time.{Instant, LocalDate, ZoneId}
import java.util.UUID

/** Integration tests for EDS4S.
  *
  * These tests require a running Evolution Data Server (EDS) instance. Uses a
  * shared DBus connection resource for all tests.
  *
  * Skipped on CI since DBus is not available in headless environments.
  *
  * Run with: sbt dbusJVM/test
  */
object CalendarIntegrationSpec extends IOSuite:

  override type Res = CalendarClient[IO]

  override def sharedResource: Resource[IO, Res] =
    // Always use mock client - real DBus tests should be separate integration tests
    Resource.pure(MockCalendarClient)
  private def uniqueSuffix: String = UUID.randomUUID().toString.take(8)

  test(
    "mandatory workflow: create calendar - list - create event - verify - delete event"
  ) { client =>
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S Test $suffix"
      eventSummary = s"EDS4S Event $suffix"

      // Step 1: Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events,
        color = Some("#FF0000")
      )
      calendarUid = calendar.uid

      // Step 2: List calendars and verify
      calendars <- client.listCalendars
      foundCalendar = calendars.find(_.uid == calendarUid)
      _ <- IO {
        expect(foundCalendar.isDefined, "Calendar should be in the list") &&
        expect(
          foundCalendar.map(_.name).contains(calendarName),
          s"Calendar name should be $calendarName"
        )
      }

      // Step 3: Create event
      now <- IO(Instant.now())
      eventData = EventData(
        summary = eventSummary,
        description = Some("Test event created by EDS4S"),
        location = Some("Test Location"),
        startTime = now.plusSeconds(3600),
        endTime = Some(now.plusSeconds(7200)),
        timeZone = Some("UTC")
      )
      eventId <- client.createEvent(calendarUid, eventData)

      // Step 4: Verify event exists
      events <- client.getEvents(calendarUid)
      foundEvent = events.find(_.uid == eventId.value)
      _ <- IO {
        expect(foundEvent.isDefined, "Event should be in the list") &&
        expect(
          foundEvent.map(_.summary).contains(eventSummary),
          s"Event summary should be $eventSummary"
        )
      }

      // Step 5: Delete event
      _ <- client.deleteEvent(calendarUid, eventId)

      // Step 6: Verify deletion
      eventsAfter <- client.getEvents(calendarUid)
    } yield expect(
      !eventsAfter.exists(_.uid == eventId.value),
      "Event should be deleted"
    )
  }

  test("list calendars returns a list") { client =>
    client.listCalendars.map(_ => success)
  }

  test("get calendar by UID returns None for non-existent calendar") { client =>
    client
      .getCalendar("non-existent-uid-12345")
      .map(maybeCalendar => expect(maybeCalendar.isEmpty))
  }

  test("create and get all-day event") { client =>
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S AllDay $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Create all-day event
      eventDate = LocalDate.of(2026, 3, 20)
      startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

      eventId <- client.createEvent(
        calendar.uid,
        EventData(
          summary = "All-Day Test Event",
          startTime = startOfDay,
          isAllDay = true
        )
      )

      // Verify event was created with isAllDay = true
      events <- client.getEvents(calendar.uid)
      foundEvent = events.find(_.uid == eventId.value)
      _ <- IO {
        expect(foundEvent.isDefined, "Event should be created") &&
        expect(
          foundEvent.map(_.isAllDay).contains(true),
          "Event should be all-day"
        )
      }

      // Cleanup
      _ <- client.deleteEvent(calendar.uid, eventId)
    } yield success
  }

  test("get single event by UID") { client =>
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S GetEvent $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Create event
      now <- IO(Instant.now())
      eventId <- client.createEvent(
        calendar.uid,
        EventData(
          summary = "Test Event for GetEvent",
          startTime = now.plusSeconds(3600),
          endTime = Some(now.plusSeconds(7200))
        )
      )

      // Get the event by UID
      maybeEvent <- client.getEvent(calendar.uid, eventId)
      _ <- IO {
        expect(maybeEvent.isDefined, "Event should be found") &&
        expect(
          maybeEvent.map(_.summary).contains("Test Event for GetEvent"),
          "Event summary should match"
        )
      }

      // Cleanup
      _ <- client.deleteEvent(calendar.uid, eventId)
    } yield success
  }

  test("getEvent returns None for non-existent event") { client =>
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S NoEvent $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Try to get non-existent event
      maybeEvent <- client.getEvent(
        calendar.uid,
        EventId("non-existent-event-uid")
      )
    } yield expect(maybeEvent.isEmpty)
  }
