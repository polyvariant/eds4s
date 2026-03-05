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

import java.time.{Instant, ZoneId, ZonedDateTime, LocalDate}
// UUID not available in Scala Native - using custom UID generator

/** Pure Scala implementation of IcalConverter for Scala Native.
  * 
  * This implementation provides basic ICS parsing and rendering without
  * depending on the Java-only ical4j library. It supports the subset of
  * iCalendar needed for EDS integration (VEVENT with basic properties).
  */
private[eds4s] class LiveIcalConverter[F[_]: Sync] extends IcalConverter[F] {

  // Counter for generating unique UIDs (since java.util.UUID is not available in Scala Native)
  private val uidCounter = new java.util.concurrent.atomic.AtomicLong(0)

  /** Generate a unique UID using nano time and counter */
  private def generateUid(): String = {
    val timestamp = System.nanoTime()
    val counter = uidCounter.getAndIncrement()
    f"$timestamp%016x-$counter%08x"
  }
  override def parseEvent(ics: String): F[Event] = Sync[F].delay {
    val lines = unfoldIcsLines(ics)
    
    // Find VEVENT content
    val veventLines = extractVeventLines(lines)
    if (veventLines.isEmpty) {
      throw EdsError.InvalidIcsFormat(ics, None)
    }
    
    parseVevent(veventLines)
  }.handleError { e =>
    throw EdsError.InvalidIcsFormat(ics, Some(e))
  }

  override def renderEvent(event: EventData): F[String] = Sync[F].delay {
    val uid = generateUid()
    renderVevent(eventToVeventLines(event, uid))
  }

  override def renderExistingEvent(event: Event): F[String] = Sync[F].delay {
    renderVevent(eventToVeventLines(event, event.uid))
  }

  // ========== Parsing ==========

  /** Unfold ICS lines (lines starting with space/tab are continuations) */
  private def unfoldIcsLines(ics: String): List[String] = {
    val rawLines = ics.replace("\r\n", "\n").replace("\r", "\n").split("\n").toList
    val (result, _) = rawLines.foldLeft((List.empty[String], false)) { case ((acc, inContinuation), line) =>
      if (line.startsWith(" ") || line.startsWith("\t")) {
        // Continuation line - append to last line
        if (acc.nonEmpty) {
          (acc.init :+ (acc.last + line.substring(1)), true)
        } else {
          (acc, inContinuation)
        }
      } else {
        (acc :+ line, false)
      }
    }
    result
  }

  /** Extract VEVENT content lines from ICS lines */
  private def extractVeventLines(lines: List[String]): List[String] = {
    val inVevent = lines.dropWhile(!_.startsWith("BEGIN:VEVENT"))
    if (inVevent.isEmpty) return Nil
    
    val content = inVevent.tail.takeWhile(!_.startsWith("END:VEVENT"))
    content
  }

  /** Parse VEVENT lines into Event */
  private def parseVevent(lines: List[String]): Event = {
    val props = lines.map(parseProperty).toMap
    
    val uid = props.get("UID").map(_.value).getOrElse("")
    val summary = props.get("SUMMARY").map(_.value).getOrElse("")
    val description = props.get("DESCRIPTION").map(_.value)
    val location = props.get("LOCATION").map(_.value)
    
    val (startTime, isAllDay, timeZone) = parseDateTimeProperty(props.get("DTSTART")).getOrElse((Instant.now(), false, None))
    val endTime = parseDateTimeProperty(props.get("DTEND")).map(_._1)
    
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

  /** Parse a single property line into (name, Property) */
  private def parseProperty(line: String): (String, IcsProperty) = {
    val colonIdx = line.indexOf(':')
    if (colonIdx < 0) {
      ("", IcsProperty("", Map.empty))
    } else {
      val name = line.substring(0, colonIdx)
      val value = line.substring(colonIdx + 1)
      
      // Parse parameters (e.g., DTSTART;TZID=Europe/Warsaw:20240115T100000)
      val semiIdx = name.indexOf(';')
      if (semiIdx < 0) {
        (name.toUpperCase, IcsProperty(value, Map.empty))
      } else {
        val baseName = name.substring(0, semiIdx).toUpperCase
        val paramStr = name.substring(semiIdx + 1)
        val params = parseParams(paramStr)
        (baseName, IcsProperty(value, params))
      }
    }
  }

  /** Parse parameters like TZID=Europe/Warsaw;VALUE=DATE */
  private def parseParams(paramStr: String): Map[String, String] = {
    paramStr.split(";")
      .map(_.split("=", 2))
      .filter(_.length == 2)
      .map(arr => arr(0).toUpperCase -> arr(1))
      .toMap
  }

  /** Parse date-time property, returns Option[(Instant, isAllDay, timeZone)] */
  private def parseDateTimeProperty(prop: Option[IcsProperty]): Option[(Instant, Boolean, Option[String])] = {
    prop match {
      case None => None
      case Some(IcsProperty(value, params)) =>
        val tzid = params.get("TZID")
        val isDateOnly = params.get("VALUE").contains("DATE") || !value.contains("T")
        
        val instant = if (isDateOnly) {
          // DATE format: YYYYMMDD
          val year = value.substring(0, 4).toInt
          val month = value.substring(4, 6).toInt
          val day = value.substring(6, 8).toInt
          LocalDate.of(year, month, day).atStartOfDay(ZoneId.of("UTC")).toInstant
        } else {
          // DATETIME format: YYYYMMDDTHHMMSS or YYYYMMDDTHHMMSSZ
          val cleanValue = value.replace("Z", "")
          val year = cleanValue.substring(0, 4).toInt
          val month = cleanValue.substring(4, 6).toInt
          val day = cleanValue.substring(6, 8).toInt
          val hour = cleanValue.substring(9, 11).toInt
          val minute = cleanValue.substring(11, 13).toInt
          val second = if (cleanValue.length > 13) cleanValue.substring(13, 15).toInt else 0
          
          val zone = tzid.map(ZoneId.of).getOrElse {
            if (value.endsWith("Z")) ZoneId.of("UTC") else ZoneId.systemDefault()
          }
          ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone).toInstant
        }
        
        Some((instant, isDateOnly, tzid))
    }
  }

  // ========== Rendering ==========

  /** Render VEVENT lines */
  private def renderVevent(lines: List[String]): String = {
    s"""BEGIN:VEVENT
       |${lines.mkString("\n")}
       |END:VEVENT""".stripMargin
  }

  /** Convert EventData to VEVENT property lines */
  private def eventToVeventLines(event: EventData, uid: String): List[String] = {
    val dtStart = renderDateTimeProp("DTSTART", event.startTime, event.isAllDay, event.timeZone)
    val dtEnd = event.endTime.map(end => renderDateTimeProp("DTEND", end, event.isAllDay, event.timeZone))
    
    List(
      Some(s"UID:$uid"),
      Some(s"SUMMARY:${escapeText(event.summary)}"),
      Some(dtStart),
      dtEnd,
      event.description.map(d => s"DESCRIPTION:${escapeText(d)}"),
      event.location.map(l => s"LOCATION:${escapeText(l)}")
    ).flatten
  }

  /** Convert Event to VEVENT property lines */
  private def eventToVeventLines(event: Event, uid: String): List[String] = {
    eventToVeventLines(
      EventData(
        summary = event.summary,
        description = event.description,
        location = event.location,
        startTime = event.startTime,
        endTime = event.endTime,
        isAllDay = event.isAllDay,
        timeZone = event.timeZone
      ),
      uid
    )
  }

  /** Render a date-time property */
  private def renderDateTimeProp(
    name: String,
    instant: Instant,
    isAllDay: Boolean,
    timeZone: Option[String]
  ): String = {
    if (isAllDay) {
      val date = LocalDate.ofInstant(instant, ZoneId.of("UTC"))
      val formatted = f"${date.getYear}%04d${date.getMonthValue}%02d${date.getDayOfMonth}%02d"
      s"${name};VALUE=DATE:$formatted"
    } else {
      val tz = timeZone.map(ZoneId.of).getOrElse(ZoneId.systemDefault())
      val zdt = ZonedDateTime.ofInstant(instant, tz)
      val formatted = f"${zdt.getYear}%04d${zdt.getMonthValue}%02d${zdt.getDayOfMonth}%02dT${zdt.getHour}%02d${zdt.getMinute}%02d${zdt.getSecond}%02d"
      timeZone match {
        case Some(tzid) => s"${name};TZID=$tzid:$formatted"
        case None => s"${name}:$formatted"
      }
    }
  }

  /** Escape special characters in text values */
  private def escapeText(text: String): String = {
    text
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace(",", "\\,")
      .replace(";", "\\;")
      .replace("\n", "\\n")
  }
}

/** Internal representation of an ICS property */
private case class IcsProperty(value: String, params: Map[String, String])
