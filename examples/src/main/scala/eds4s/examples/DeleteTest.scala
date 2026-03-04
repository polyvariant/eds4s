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

/** Simple delete test to isolate the issue
  */
object DeleteTest extends IOApp.Simple {

  val SystemCalendarUid = "system-calendar"

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("Listing events...")
        events <- client.getEvents(SystemCalendarUid)
        _ <- IO.println(s"Found ${events.size} events")

        // Find an event to delete (pick the first test event)
        testEvent <- events.find(_.summary.startsWith("EDS4S Test")) match {
          case Some(e) =>
            IO.println(s"Found test event: ${e.uid} - ${e.summary}").as(e)
          case None =>
            IO.println("No test events found, creating one...") *>
              client
                .createEvent(
                  SystemCalendarUid,
                  EventData(
                    summary =
                      s"EDS4S Test Event ${java.util.UUID.randomUUID().toString}",
                    description = Some("Test event for deletion"),
                    startTime = java.time.Instant.now(),
                    endTime = None,
                    isAllDay = true,
                    timeZone = Some("UTC")
                  )
                )
                .flatMap { eventId =>
                  client
                    .getEvents(SystemCalendarUid)
                    .map(_.find(_.uid == eventId.value))
                    .flatMap {
                      case Some(e) =>
                        IO.println(s"Created test event: ${e.uid}").as(e)
                      case None =>
                        IO.raiseError(
                          new RuntimeException("Failed to find created event")
                        )
                    }
                }
        }

        _ <- IO.println(s"\nAttempting to delete event: ${testEvent.uid}")
        _ <- client.deleteEvent(SystemCalendarUid, EventId(testEvent.uid))
        _ <- IO.println(s"✅ Successfully deleted event!")
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
