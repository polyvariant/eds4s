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

package eds4s.examples

import eds4s.*
import eds4s.dbus.DBusCalendarClient
import cats.effect.*
import cats.syntax.all.*
import java.time.Instant

/** QuickStart example demonstrating EDS4S usage.
  *
  * Run with: sbt "examples/runMain eds4s.examples.QuickStart"
  *
  * Prerequisites:
  *   - Evolution Data Server must be running
  *   - DBus session must be available
  */
object QuickStart extends IOApp.Simple {

  def run: IO[Unit] = {
    val program = DBusCalendarClient.resource[IO].use { client =>
      for _ <- IO.println("=== EDS4S QuickStart Example ===\n")

      // Step 1: List existing calendars
      _ <- IO.println("Step 1: Listing existing calendars...")
      calendars <- client.listCalendars
      _ <- IO.println(s"Found ${calendars.size} calendars:")
      _ <- calendars.traverse(c => IO.println(s"  - ${c.name} (${c.uid})"))
      _ <- IO.println("")

      // Step 2: Create a new calendar
      calendarName = s"EDS4S Example ${System.currentTimeMillis() % 10000}"
      _ <- IO.println(s"Step 2: Creating calendar '$calendarName'...")
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events,
        color = Some("#3465A4")
      )
      _ <- IO.println(s"Created calendar with UID: ${calendar.uid}")
      _ <- IO.println("")

      // Step 3: Create an event
      _ <- IO.println("Step 3: Creating an event...")
      now <- IO(Instant.now())
      eventId <- client.createEvent(
        calendar.uid,
        EventData(
          summary = "Team Meeting",
          description = Some("Weekly team synchronization meeting"),
          location = Some("Conference Room A"),
          startTime = now.plusSeconds(3600), // 1 hour from now
          endTime = Some(now.plusSeconds(7200)), // 2 hours from now
          timeZone = Some("UTC")
        )
      )
      _ <- IO.println(s"Created event with ID: ${eventId.value}")
      _ <- IO.println("")

      // Step 4: List events
      _ <- IO.println("Step 4: Listing events in the calendar...")
      events <- client.getEvents(calendar.uid)
      _ <- IO.println(s"Found ${events.size} events:")
      _ <- events.traverse { e =>
        IO.println(s"  - ${e.summary}")
          *> IO.println(s"    Start: ${e.startTime}")
          *> IO.println(s"    End: ${e.endTime}")
      }
      _ <- IO.println("")

      // Step 5: Delete the event
      _ <- IO.println(s"Step 5: Deleting event ${eventId.value}...")
      _ <- client.deleteEvent(calendar.uid, eventId)
      _ <- IO.println("Event deleted")
      _ <- IO.println("")

      // Step 6: Verify deletion
      _ <- IO.println("Step 6: Verifying event deletion...")
      eventsAfterDelete <- client.getEvents(calendar.uid)
      _ <- IO.println(s"Events after deletion: ${eventsAfterDelete.size}")
      _ <- IO.println("")

      // Step 7: Clean up - delete the calendar
      _ <- IO.println(
        s"Step 7: Cleaning up - deleting calendar ${calendar.uid}..."
      )
      _ <- client.deleteCalendar(calendar.uid)
      _ <- IO.println("Calendar deleted")
      _ <- IO.println("")

      _ <- IO.println("=== Example completed successfully! ===")
      yield ()
    }

    program.handleErrorWith { e =>
      IO.println(s"Error: ${e.getMessage}")
        *> IO.println(s"Make sure Evolution Data Server is running.")
        *> IO(e.printStackTrace())
    }
  }
}
