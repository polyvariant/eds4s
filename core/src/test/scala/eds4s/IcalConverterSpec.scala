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

package eds4s

import weaver.SimpleIOSuite
import cats.effect.IO
import cats.syntax.all.*
import java.time.Instant
import java.time.ZoneId

object IcalConverterSpec extends SimpleIOSuite {

  // Sample ICS string for a simple event
  private val sampleIcs =
    """BEGIN:VCALENDAR
      |PRODID:-//EDS4S//Scala 3//EN
      |VERSION:2.0
      |CALSCALE:GREGORIAN
      |BEGIN:VEVENT
      |UID:test-uid-123
      |SUMMARY:Test Event
      |DESCRIPTION:Test Description
      |LOCATION:Test Location
      |DTSTART:20240101T100000Z
      |DTEND:20240101T110000Z
      |END:VEVENT
      |END:VCALENDAR
      |""".stripMargin

  test("parseEvent should parse a valid ICS string") {
    val converter = IcalConverter[IO]

    for event <- converter.parseEvent(sampleIcs)
    yield expect(event.uid == "test-uid-123") &&
      expect(event.summary == "Test Event") &&
      expect(event.description.contains("Test Description")) &&
      expect(event.location.contains("Test Location"))
  }

  test(
    "renderEvent should create a valid VEVENT string (not wrapped in VCALENDAR)"
  ) {
    val converter = IcalConverter[IO]
    val now = Instant.parse("2024-01-01T10:00:00Z")
    val endTime = Instant.parse("2024-01-01T11:00:00Z")

    val eventData = EventData(
      summary = "Test Event",
      description = Some("Test Description"),
      location = Some("Test Location"),
      startTime = now,
      endTime = Some(endTime),
      timeZone = Some("UTC")
    )

    for ics <- converter.renderEvent(eventData)
    yield
      // EDS expects just VEVENT, not wrapped in VCALENDAR
      expect(ics.contains("BEGIN:VEVENT")) &&
        expect(ics.contains("SUMMARY:Test Event")) &&
        expect(ics.contains("DESCRIPTION:Test Description")) &&
        expect(ics.contains("LOCATION:Test Location")) &&
        expect(ics.contains("END:VEVENT"))
  }

  test("round-trip: render then parse should preserve event data") {
    val converter = IcalConverter[IO]
    val now = Instant.parse("2024-01-01T10:00:00Z")
    val endTime = Instant.parse("2024-01-01T11:00:00Z")

    val originalEvent = EventData(
      summary = "Round Trip Test",
      description = Some("Testing round trip"),
      location = Some("Test Location"),
      startTime = now,
      endTime = Some(endTime),
      timeZone = Some("UTC")
    )

    for ics <- converter.renderEvent(originalEvent)
    parsedEvent <- converter.parseEvent(ics)
    yield expect(parsedEvent.summary == originalEvent.summary) &&
      expect(parsedEvent.description == originalEvent.description) &&
      expect(parsedEvent.location == originalEvent.location)
  }

  test("parseEvent should fail for invalid ICS") {
    val converter = IcalConverter[IO]
    val invalidIcs = "NOT A VALID ICS STRING"

    for result <- converter.parseEvent(invalidIcs).attempt
    yield expect(result.isLeft, "Parsing invalid ICS should fail")
  }
}
