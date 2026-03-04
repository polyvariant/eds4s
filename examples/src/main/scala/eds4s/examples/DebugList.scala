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

object DebugList extends IOApp.Simple {
  def run: IO[Unit] = {
    DBusCalendarClient
      .resource[IO]
      .use { client =>
        for _ <- IO.println(
          "=== Debug: List all calendars with full info ===\n"
        )
        calendars <- client.listCalendars
        _ <- calendars.traverse { c =>
          IO.println(s"Name: ${c.name}")
            *> IO.println(s"  UID: ${c.uid}")
            *> IO.println(s"  Backend: ${c.backend}")
            *> IO.println(s"  Type: ${c.calendarType}")
            *> IO.println(s"  Visible: ${c.visible}")
            *> IO.println(s"  Writable: ${c.writable}")
            *> IO.println(s"  Color: ${c.color}")
            *> IO.println(s"  URI: ${c.sourceUri}")
            *> IO.println("")
        }
        _ <- IO.println(s"\nTotal calendars: ${calendars.size}")
        _ <- IO.println(s"\nLooking for 'system-calendar'...")
        found = calendars.find(_.uid == "system-calendar")
        _ <- found match {
          case Some(c) => IO.println(s"FOUND: ${c.name}")
          case None    =>
            IO.println("NOT FOUND by UID. Checking by name...")
              *> calendars
                .find(_.name.toLowerCase.contains("personal"))
                .traverse(c =>
                  IO.println(s"Found by name: ${c.name} (uid: ${c.uid})")
                )
        } yield ()
      }
      .handleErrorWith { e =>
        IO.println(s"Error: ${e.getMessage}") *> IO(e.printStackTrace())
      }
  }
}
