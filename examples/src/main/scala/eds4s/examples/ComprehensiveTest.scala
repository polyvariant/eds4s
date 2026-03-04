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

package eds4s.examples

import eds4s.*
import eds4s.dbus.DBusCalendarClient
import cats.effect.*
import cats.syntax.all.*
import java.time.{Instant, LocalDate, ZoneId}

import java.util.UUID

/** Comprehensive test of all EDS4S operations using the existing
  * system-calendar. Tests: list calendars, create event, get events, get event,
  * delete event.
  */
object ComprehensiveTest extends IOApp.Simple {

  val SystemCalendarUid = "system-calendar"

  def run: IO[Unit] = {
    val separator = "=" * 60
    val testEventId = UUID.randomUUID().toString
    val eventDate = LocalDate.of(2026, 3, 4)
    val startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println(separator)
        _ <- IO.println("TEST 1: List calendars")
        calendars <- client.listCalendars
        _ <- IO.println(s"Found ${calendars.size} calendars")

        // Find system-calendar
        _ <- calendars.find(c => c.uid == SystemCalendarUid) match {
          case Some(c) =>
            IO.println(s"✅ Found system-calendar: ${c.name}")
          case None =>
            IO.raiseError(
              new RuntimeException(
                s"system-calendar not found! Available UIDs: ${calendars.map(_.uid).take(5).mkString(", ")}"
              )
            )
        }

        _ <- IO.println("")
        _ <- IO.println("TEST 2: List existing events")
        existingEvents <- client.getEvents(SystemCalendarUid)
        _ <- IO.println(s"Found ${existingEvents.size} existing events")
        _ <- IO.println("")

        _ <- IO.println(s"TEST 3: Create event (ID: $testEventId)")
        eventId <- client.createEvent(
          SystemCalendarUid,
          EventData(
            summary = s"EDS4S Test Event $testEventId",
            description = Some("Test event for EDS4S library verification"),
            location = Some("Test Location"),
            startTime = startOfDay,
            endTime = None,
            isAllDay = true,
            timeZone = Some("UTC")
          )
        )
        _ <- IO.println(s"✅ Created event: ${eventId.value}")
        _ <- IO.println("")

        _ <- IO.println("TEST 4: Verify event in list")
        eventsAfterCreate <- client.getEvents(SystemCalendarUid)
        createdEvent <- eventsAfterCreate.find(_.uid == eventId.value) match {
          case Some(e) =>
            IO.println(s"✅ Found event in list: ${e.summary}").as(e)
          case None =>
            IO.raiseError(
              new RuntimeException(
                s"Created event ${eventId.value} not found in list!"
              )
            )
        }
        _ <- IO.println("")

        _ <- IO.println("TEST 5: Get event by UID")
        fetchedEvent <- client.getEvent(SystemCalendarUid, eventId)
        _ <- fetchedEvent match {
          case Some(e) =>
            IO.println(s"✅ Retrieved event: ${e.summary}")
          case None =>
            IO.raiseError(
              new RuntimeException(s"Could not fetch event ${eventId.value}")
            )
        }
        _ <- IO.println("")

        _ <- IO.println(s"TEST 6: Delete event")
        _ <- client.deleteEvent(SystemCalendarUid, eventId)
        _ <- IO.println(s"✅ Deleted event ${eventId.value}")
        _ <- IO.println("")

        _ <- IO.println("TEST 7: Verify deletion")
        eventsAfterDelete <- client.getEvents(SystemCalendarUid)
        stillExists = eventsAfterDelete.exists(_.uid == eventId.value)
        _ <-
          if stillExists then IO.raiseError(
            new RuntimeException(
              s"Event ${eventId.value} still exists after deletion!"
            )
          )
          else
            IO.println(s"✅ Event successfully deleted - no longer in list")
        _ <- IO.println("")

        _ <- IO.println(separator)
        _ <- IO.println("ALL TESTS PASSED!")
        _ <- IO.println(separator)
        yield ()
      }
      .handleErrorWith { e =>
        IO.println("") *>
          IO.println("=" * 60) *>
          IO.println(s"FAILED: ${e.getMessage}") *>
          IO.println("=" * 60) *>
          IO(e.printStackTrace())
      }
  }
}
