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
package eds4s.examples

import eds4s.*
import eds4s.dbus.DBusCalendarClient
import cats.effect.*
import cats.syntax.all.*
import java.time.{LocalDate, ZoneId}

object SimpleCreateDelete extends IOApp.Simple {
  // Use a writable Caldav calendar (system-calendar is read-only for deletes)
  private val WritableCalendarUid =
    "c239c5ebdca109a72c1dc11c96cb406f62679a46" // Osobiste

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("=== Simple Create/Delete Test ===\n")

        // Find the writable calendar
        _ <- IO.println(s"Using calendar: $WritableCalendarUid")
        calendars <- client.listCalendars
        calendar <- calendars.find(_.uid == WritableCalendarUid) match {
          case Some(c) => IO.println(s"Found: ${c.name}") *> IO.pure(c)
          case None    =>
            IO.raiseError(
              new RuntimeException(s"Calendar '$WritableCalendarUid' not found")
            )
        }

        // Create event
        eventDate = LocalDate.of(2026, 3, 5)
        startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

        eventData = EventData(
          summary = "Simple Test Event",
          startTime = startOfDay,
          isAllDay = true
        )

        _ <- IO.println("Creating event...")
        eventId <- client.createEvent(WritableCalendarUid, eventData)
        _ <- IO.println(s"✅ Created: ${eventId.value}")

        // Immediately delete
        _ <- IO.println("Deleting event...")
        _ <- client.deleteEvent(WritableCalendarUid, eventId)
        _ <- IO.println("✅ Deleted successfully!")

        _ <- IO.println("\n=== TEST PASSED ===")
        yield ()
      }
      .handleErrorWith { e =>
        IO.println(s"\n❌ FAILED: ${e.getMessage}")
      }
  }
}
