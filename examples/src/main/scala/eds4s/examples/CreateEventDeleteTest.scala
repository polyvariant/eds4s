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
import java.util.UUID

/** Test that creates an event and deletes it in an existing writable calendar.
  * Uses the eds4s-test calendar that was created manually.
  */
object CreateEventDeleteTest extends IOApp.Simple {
  // Use the test calendar we created manually
  private val TestCalendarUid = "eds4s-test-1772577343"

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("=== Create Event + Delete Test ===\n")

        // Verify the test calendar exists
        _ <- IO.println(s"Using test calendar: $TestCalendarUid")
        calendars <- client.listCalendars
        testCal <- calendars.find(_.uid == TestCalendarUid) match {
          case Some(c) => IO.println(s"Found: ${c.name}\n")
          case None    =>
            IO.raiseError(
              new RuntimeException(
                s"Test calendar '$TestCalendarUid' not found. Please create it first."
              )
            )
        }

        // Step 1: Create an event
        eventDate = LocalDate.of(2026, 3, 10)
        startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

        _ <- IO.println(s"Step 1: Creating event...")
        eventId <- client.createEvent(
          TestCalendarUid,
          EventData(
            summary = s"Test Event ${UUID.randomUUID().toString.take(8)}",
            description = Some("This event will be deleted"),
            startTime = startOfDay,
            isAllDay = true
          )
        )
        _ <- IO.println(s"✅ Created event: ${eventId.value}\n")

        // Step 2: Verify event exists
        _ <- IO.println(s"Step 2: Verifying event exists...")
        eventsBefore <- client.getEvents(TestCalendarUid)
        eventExistsBefore = eventsBefore.exists(_.uid == eventId.value)
        _ <-
          if (!eventExistsBefore)
            IO.raiseError(new RuntimeException("Event was not created!"))
          else
            IO.println(
              s"✅ Event found in calendar (${eventsBefore.size} total events)\n"
            )

        // Step 3: Delete the event
        _ <- IO.println(s"Step 3: Deleting event...")
        _ <- client.deleteEvent(TestCalendarUid, eventId)
        _ <- IO.println(s"✅ Delete call succeeded\n")

        // Step 4: Verify event is gone
        _ <- IO.println(s"Step 4: Verifying event is deleted...")
        eventsAfter <- client.getEvents(TestCalendarUid)
        eventExistsAfter = eventsAfter.exists(_.uid == eventId.value)
        _ <-
          if (eventExistsAfter)
            IO.raiseError(
              new RuntimeException(s"Event still exists after deletion!")
            )
          else
            IO.println(
              s"✅ Event successfully deleted (calendar now has ${eventsAfter.size} events)\n"
            )

        _ <- IO.println("=== TEST PASSED ===")
        yield ()
      }
      .handleErrorWith { e =>
        IO.println(s"\n❌ TEST FAILED: ${e.getMessage}")
          *> IO(e.printStackTrace())
      }
  }
}
