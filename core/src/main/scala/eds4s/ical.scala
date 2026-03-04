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

import cats.effect.Sync
import cats.syntax.all.*
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.CompatibilityHints

import java.io.StringReader
import java.time.{Instant, ZoneId, ZonedDateTime, LocalDate}
import java.net.URI
import java.util.UUID

/** Converter between domain types and iCalendar format */
trait IcalConverter[F[_]] {
  def parseEvent(ics: String): F[Event]
  def renderEvent(event: EventData): F[String]
  def renderExistingEvent(event: Event): F[String]
}

object IcalConverter {
  // Initialize compatibility hints once at class loading
  // This is a global setting in ical4j, so we set it once
  private val initCompatibilityHints: Unit = {
    CompatibilityHints.setHintEnabled(
      CompatibilityHints.KEY_RELAXED_PARSING,
      true
    )
    CompatibilityHints.setHintEnabled(
      CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY,
      true
    )
  }

  def apply[F[_]: Sync]: IcalConverter[F] = {
    // Ensure hints are initialized by referencing the val
    val _: Unit = initCompatibilityHints
    new LiveIcalConverter[F]
  }
}

private class LiveIcalConverter[F[_]: Sync] extends IcalConverter[F] {

  override def parseEvent(ics: String): F[Event] = Sync[F]
    .delay {
      val builder = new CalendarBuilder()
      val trimmed = ics.trim

      // Check if this is just a VEVENT or a full VCALENDAR
      val calendar = if (trimmed.startsWith("BEGIN:VEVENT")) {
        // Wrap in VCALENDAR for parsing
        val wrapped =
          s"BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//EDS4S//EN\n$ics\nEND:VCALENDAR"
        builder.build(new StringReader(wrapped))
      } else {
        builder.build(new StringReader(ics))
      }

      val components = calendar.getComponents(Component.VEVENT)
      if (components.isEmpty) {
        throw EdsError.InvalidIcsFormat(ics, None)
      }
      val vevent = components.get(0).asInstanceOf[VEvent]
      veventToEvent(vevent)
    }
    .handleError { e =>
      throw EdsError.InvalidIcsFormat(ics, Some(e))
    }

  override def renderEvent(event: EventData): F[String] = Sync[F].delay {
    val vevent = eventDataToVEvent(event)
    // EDS expects just the VEVENT component, not wrapped in VCALENDAR
    vevent.toString
  }

  override def renderExistingEvent(event: Event): F[String] = Sync[F].delay {
    val vevent = eventToVEvent(event)
    // EDS expects just the VEVENT component, not wrapped in VCALENDAR
    vevent.toString
  }

  private def veventToEvent(vevent: VEvent): Event = {
    val uid = vevent.getUid.orElse(null) match {
      case null => ""
      case u    => u.getValue
    }

    val summary = Option(vevent.getSummary).map(_.getValue).getOrElse("")
    val description = Option(vevent.getDescription).map(_.getValue)
    val location = Option(vevent.getLocation).map(_.getValue)

    val startTime = vevent.getStartDate.orElse(null) match {
      case null    => Instant.now()
      case dtStart =>
        dtStart.getDate match {
          case localDate: LocalDate =>
            localDate.atStartOfDay(ZoneId.of("UTC")).toInstant
          case _ =>
            try {
              Instant.from(dtStart.getDate)
            } catch {
              case _: Exception => Instant.now()
            }
        }
    }

    val endTime = Option(vevent.getEndDate.orElse(null)).map { dt =>
      dt.getDate match {
        case localDate: LocalDate =>
          localDate.atStartOfDay(ZoneId.of("UTC")).toInstant
        case _ =>
          try {
            Instant.from(dt.getDate)
          } catch {
            case _: Exception => null
          }
      }
    }

    val isAllDay = vevent.getStartDate.orElse(null) match {
      case null    => false
      case dtStart => dtStart.getDate.isInstanceOf[LocalDate]
    }

    val timeZone = vevent.getStartDate.orElse(null) match {
      case null    => None
      case dtStart =>
        val tzidParam = dtStart.getParameter(Property.TZID)
        if (tzidParam.isPresent) Some(tzidParam.get().getValue) else None
    }

    Event(
      uid = uid,
      summary = summary,
      description = description,
      location = location,
      startTime = startTime,
      endTime = endTime,
      isAllDay = isAllDay,
      timeZone = timeZone
    )
  }

  /** Common data needed for VEvent construction */
  private case class VEventData(
      summary: String,
      description: Option[String],
      location: Option[String],
      startTime: Instant,
      endTime: Option[Instant],
      isAllDay: Boolean,
      timeZone: Option[String]
  )

  /** Build a VEvent from common data */
  private def buildVEvent(data: VEventData): VEvent = {
    val tz = data.timeZone.map(ZoneId.of).getOrElse(ZoneId.systemDefault())

    val vevent = if (data.isAllDay) {
      val startDate = LocalDate.ofInstant(data.startTime, ZoneId.of("UTC"))
      data.endTime match {
        case Some(end) =>
          val endDate = LocalDate.ofInstant(end, ZoneId.of("UTC"))
          new VEvent(startDate, endDate, data.summary)
        case None =>
          new VEvent(startDate, data.summary)
      }
    } else {
      val start = ZonedDateTime.ofInstant(data.startTime, tz)
      data.endTime match {
        case Some(end) =>
          val endZoned = ZonedDateTime.ofInstant(end, tz)
          new VEvent(start, endZoned, data.summary)
        case None =>
          new VEvent(start, data.summary)
      }
    }

    data.description.foreach(d => vevent.add(new Description(d)))
    data.location.foreach(l => vevent.add(new Location(l)))
    vevent
  }

  private def eventDataToVEvent(data: EventData): VEvent = {
    val vevent = buildVEvent(
      VEventData(
        data.summary,
        data.description,
        data.location,
        data.startTime,
        data.endTime,
        data.isAllDay,
        data.timeZone
      )
    )
    vevent.add(new Uid(UUID.randomUUID().toString))
    vevent
  }

  private def eventToVEvent(event: Event): VEvent = {
    val vevent = buildVEvent(
      VEventData(
        event.summary,
        event.description,
        event.location,
        event.startTime,
        event.endTime,
        event.isAllDay,
        event.timeZone
      )
    )
    vevent.add(new Uid(event.uid))
    vevent
  }
}
