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

/** Test event operations using the existing system-calendar. Run with: sbt
  * "project examples" "runMain eds4s.examples.TestEventOps"
  */
object TestEventOps extends IOApp.Simple {

  val SystemCalendarUid = "system-calendar"

  def run: IO[Unit] = {
    // Define values before the for-comprehension
    val eventDate = LocalDate.of(2026, 3, 4)
    val startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("=== Testing Event Operations ===\n")

        // Step 1: List calendars to verify system-calendar exists
        _ <- IO.println("Step 1: Listing calendars...")
        calendars <- client.listCalendars
        _ <- IO.println(s"Found ${calendars.size} calendars:")
        _ <- calendars
          .traverse(c => IO.println(s"  - ${c.name} (uid: ${c.uid})"))
        _ <- IO.println("")

        // Verify system-calendar exists
        systemCal <- calendars.find(_.uid == SystemCalendarUid) match {
          case Some(c) => IO.pure(c)
          case None    =>
            IO.raiseError(
              new RuntimeException(s"Calendar '$SystemCalendarUid' not found")
            )
        }

        // Step 2: List existing events
        _ <- IO.println(s"Step 2: Listing events in ${systemCal.name}...")
        existingEvents <- client.getEvents(SystemCalendarUid)
        _ <- IO.println(s"Found ${existingEvents.size} existing events")
        _ <- IO.println("")

        // Step 3: Create a test event for March 4, 2026 (all-day)
        _ <- IO.println(
          "Step 3: Creating all-day test event for March 4, 2026..."
        )

        eventId <- client.createEvent(
          SystemCalendarUid,
          EventData(
            summary = "EDS4S Test Event - March 4th",
            description = Some("Test event created by EDS4S library"),
            location = Some("Test Location"),
            startTime = startOfDay,
            endTime = None,
            isAllDay = true,
            timeZone = Some("UTC")
          )
        )
        _ <- IO.println(s"✓ Created event with ID: ${eventId.value}\n")

        // Step 4: Verify event was created by listing events again
        _ <- IO.println("Step 4: Verifying event creation...")
        eventsAfterCreate <- client.getEvents(SystemCalendarUid)
        createdEvent <- eventsAfterCreate.find(_.uid == eventId.value) match {
          case Some(e) => IO.pure(e)
          case None    =>
            IO.raiseError(
              new RuntimeException(
                s"Created event ${eventId.value} not found in list"
              )
            )
        }
        _ <- IO.println(s"✓ Found event: ${createdEvent.summary}")
        _ <- IO.println(s"  Start: ${createdEvent.startTime}")
        _ <- IO.println(s"  All-day: ${createdEvent.isAllDay}")
        _ <- IO.println("")

        // Step 5: Get the event directly by ID
        _ <- IO.println("Step 5: Getting event by ID...")
        fetchedEvent <- client.getEvent(SystemCalendarUid, eventId)
        _ <- fetchedEvent match {
          case Some(e) => IO.println(s"✓ Retrieved event: ${e.summary}")
          case None    =>
            IO.raiseError(
              new RuntimeException(s"Could not fetch event ${eventId.value}")
            )
        }
        _ <- IO.println("")

        // Step 6: Delete the event
        _ <- IO.println(s"Step 6: Deleting event ${eventId.value}...")
        _ <- client.deleteEvent(SystemCalendarUid, eventId)
        _ <- IO.println("✓ Event deleted\n")

        // Step 7: Verify deletion
        _ <- IO.println("Step 7: Verifying deletion...")
        eventsAfterDelete <- client.getEvents(SystemCalendarUid)
        stillExists = eventsAfterDelete.exists(_.uid == eventId.value)
        _ <-
          if stillExists then IO.raiseError(
            new RuntimeException(
              s"Event ${eventId.value} still exists after deletion!"
            )
          )
          else
            IO.println("✓ Event successfully deleted")
        _ <- IO.println("")

        _ <- IO.println("=== All tests passed! ===")
        yield ()
      }
      .handleErrorWith { e =>
        IO.println(s"\n❌ TEST FAILED: ${e.getMessage}") *>
          IO(e.printStackTrace())
      }
  }
}
