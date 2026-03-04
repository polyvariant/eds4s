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
import java.time.{Instant, LocalDate, ZoneId}

/** Demo program that creates a TEST calendar and an all-day event for March
  * 4th, 2026.
  *
  * Run with: sbt "examples/runMain eds4s.examples.DemoTest"
  *
  * Prerequisites:
  *   - Evolution Data Server must be running
  *   - DBus session must be available
  */
object DemoTest extends IOApp.Simple {

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("=== EDS4S Demo Test ===\n")

        // Step 1: Create calendar named "TEST"
        _ <- IO.println("Creating calendar 'TEST'...")
        calendar <- client.createCalendar(
          name = "TEST",
          backend = CalendarBackend.Local,
          calendarType = CalendarType.Events,
          color = Some("#FF5733")
        )
        _ <- IO.println(s"✓ Created calendar: ${calendar.uid}\n")

        // Step 2: Create all-day event for March 4th, 2026
        _ <- IO.println("Creating all-day event for March 4th, 2026...")

        // For all-day event, use start of day in UTC
        eventDate = LocalDate.of(2026, 3, 4)
        startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

        eventId <- client.createEvent(
          calendar.uid,
          EventData(
            summary = "Test Event - March 4th 2026",
            description = Some("All-day test event created by EDS4S"),
            startTime = startOfDay,
            endTime = None, // No end time for all-day event
            isAllDay = true,
            timeZone = Some("UTC")
          )
        )
        _ <- IO.println(s"✓ Created event: ${eventId.value}\n")

        // Step 3: Verify by listing events
        _ <- IO.println("Verifying - listing events in TEST calendar:")
        events <- client.getEvents(calendar.uid)
        _ <- events.traverse { e =>
          IO.println(s"  - ${e.summary} (all-day: ${e.isAllDay})")
        }
        _ <- IO.println("")

        _ <- IO.println("=== Demo completed! ===")
        _ <- IO.println(
          "Calendar 'TEST' created with all-day event for March 4th, 2026."
        )
        _ <- IO.println("Check your calendar application to see the results.")
        yield ()
      }
      .handleErrorWith { e =>
        IO.println(s"Error: ${e.getMessage}") *>
          IO.println("Make sure Evolution Data Server is running.") *>
          IO(e.printStackTrace())
      }
  }
}
