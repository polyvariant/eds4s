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
package eds4s.examples

import eds4s.*
import eds4s.dbus.DBusCalendarClient
import cats.effect.*
import cats.syntax.all.*
import java.time.{LocalDate, ZoneId}
import java.util.UUID

/** Test that creates a calendar, creates an event, deletes the event. Full
  * workflow test including calendar creation.
  */
object CreateCalendarTest extends IOApp.Simple {
  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("=== Full Calendar + Event Test ===\n")

        // Step 1: Create a new calendar
        calendarName = s"EDS4S Full Test ${UUID.randomUUID().toString.take(8)}"
        _ <- IO.println(s"Step 1: Creating calendar: $calendarName")
        calendar <- client.createCalendar(
          name = calendarName,
          backend = CalendarBackend.Local,
          calendarType = CalendarType.Events,
          color = Some("#00FF00")
        )
        calendarUid = calendar.uid
        _ <- IO.println(s"✅ Created calendar: $calendarUid\n")

        // Step 2: List calendars and verify our calendar exists
        _ <- IO.println(s"Step 2: Verifying calendar exists...")
        calendars <- client.listCalendars
        foundCalendar = calendars.find(_.uid == calendarUid)
        _ <-
          if (foundCalendar.isEmpty)
            IO.raiseError(new RuntimeException("Calendar not found in list!"))
          else
            IO.println(
              s"✅ Calendar found: ${foundCalendar.map(_.name).getOrElse("")}\n"
            )

        // Step 3: Create an event
        eventDate = LocalDate.of(2026, 3, 15)
        startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

        _ <- IO.println(s"Step 3: Creating event...")
        eventId <- client.createEvent(
          calendarUid,
          EventData(
            summary = s"Test Event ${UUID.randomUUID().toString.take(8)}",
            description = Some("This event will be deleted"),
            startTime = startOfDay,
            isAllDay = true
          )
        )
        _ <- IO.println(s"✅ Created event: ${eventId.value}\n")

        // Step 4: Verify event exists
        _ <- IO.println(s"Step 4: Verifying event exists...")
        eventsBefore <- client.getEvents(calendarUid)
        eventExistsBefore = eventsBefore.exists(_.uid == eventId.value)
        _ <-
          if (!eventExistsBefore)
            IO.raiseError(new RuntimeException("Event was not created!"))
          else
            IO.println(
              s"✅ Event found in calendar (${eventsBefore.size} total events)\n"
            )

        // Step 5: Delete the event
        _ <- IO.println(s"Step 5: Deleting event...")
        _ <- client.deleteEvent(calendarUid, eventId)
        _ <- IO.println(s"✅ Delete call succeeded\n")

        // Step 6: Verify event is gone
        _ <- IO.println(s"Step 6: Verifying event is deleted...")
        eventsAfter <- client.getEvents(calendarUid)
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
