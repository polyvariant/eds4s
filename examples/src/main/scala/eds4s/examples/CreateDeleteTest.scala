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

/** Test create and delete in sequence
  */
object CreateDeleteTest extends IOApp.Simple {

  val SystemCalendarUid = "system-calendar"

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("Creating a new event...")
        eventId <- client.createEvent(
          SystemCalendarUid,
          EventData(
            summary =
              s"Test Delete Event ${java.util.UUID.randomUUID().toString}",
            description = Some("This event will be deleted"),
            startTime = Instant.now(),
            endTime = None,
            isAllDay = true,
            timeZone = Some("UTC")
          )
        )
        _ <- IO.println(s"Created event: ${eventId.value}")

        _ <- IO.println("\nVerifying event exists...")
        events <- client.getEvents(SystemCalendarUid)
        _ <- events.find(_.uid == eventId.value) match {
          case Some(e) => IO.println(s"Found event: ${e.summary}")
          case None    =>
            IO.raiseError(
              new RuntimeException("Event not found after creation!")
            )
        }

        _ <- IO.println("\nAttempting to delete the event...")
        _ <- client.deleteEvent(SystemCalendarUid, eventId)
        _ <- IO.println("✅ Successfully deleted event!")

        _ <- IO.println("\nVerifying deletion...")
        eventsAfter <- client.getEvents(SystemCalendarUid)
        _ <- eventsAfter.find(_.uid == eventId.value) match {
          case Some(_) =>
            IO.raiseError(
              new RuntimeException("Event still exists after deletion!")
            )
          case None => IO.println("✅ Event confirmed deleted")
        } yield ()
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
