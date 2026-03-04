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
import cats.effect.*
import cats.syntax.all.*
import java.time.{Instant, LocalDate, ZoneId}

object DebugIcs extends IOApp.Simple {
  def run: IO[Unit] = {
    val converter = IcalConverter[IO]

    val eventDate = LocalDate.of(2026, 3, 4)
    val startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

    val eventData = EventData(
      summary = "Test Event",
      description = Some("Test description"),
      location = Some("Test Location"),
      startTime = startOfDay,
      endTime = None,
      isAllDay = true,
      timeZone = Some("UTC")
    )

    for ics <- converter.renderEvent(eventData)
    _ <- IO.println("=== Generated ICS ===")
    _ <- IO.println(ics)
    _ <- IO.println("===================")
    _ <- IO.println(s"Length: ${ics.length}")
    _ <- IO.println(s"Lines: ${ics.linesIterator.size}")
    yield ()
  }
}
