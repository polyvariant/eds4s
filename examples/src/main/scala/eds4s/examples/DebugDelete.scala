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

/** Debug test to understand the delete issue
  */
object DebugDelete extends IOApp.Simple {

  val SystemCalendarUid = "system-calendar"

  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println("Step 1: List calendars...")
        calendars <- client.listCalendars
        _ <- IO.println(s"Found ${calendars.size} calendars")

        _ <- IO.println("\nStep 2: List events...")
        events <- client.getEvents(SystemCalendarUid)
        _ <- IO.println(s"Found ${events.size} events")

        _ <- IO.println("\nStep 3: Get a single event...")
        testEvent <- events.find(_.summary.startsWith("EDS4S Test")) match {
          case Some(e) => IO.println(s"Found: ${e.uid}").as(e)
          case None    =>
            IO.raiseError(new RuntimeException("No test events found"))
        }

        _ <- IO.println("\nStep 4: Get the same event by UID...")
        fetched <- client.getEvent(SystemCalendarUid, EventId(testEvent.uid))
        _ <- fetched match {
          case Some(e) => IO.println(s"Retrieved: ${e.uid}")
          case None    => IO.raiseError(new RuntimeException("Event not found"))
        }

        _ <- IO.println("\nStep 5: List events again...")
        events2 <- client.getEvents(SystemCalendarUid)
        _ <- IO.println(s"Found ${events2.size} events")

        _ <- IO.println("\nStep 6: Attempt delete...")
        _ <- client.deleteEvent(SystemCalendarUid, EventId(testEvent.uid))
        _ <- IO.println("✅ Delete succeeded!")
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
