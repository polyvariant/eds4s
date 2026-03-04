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

package eds4s

import java.time.Instant

// Calendar types
enum CalendarType {
  case Events // VEVENT - calendar events
  case Tasks // VTODO - tasks/todos
  case Memos // VJOURNAL - memos/notes
}

enum CalendarBackend {
  case Local // Local file-based
  case WebCal // Web calendar (HTTP/HTTPS)
  case Google // Google Calendar
  case Caldav // CalDAV server
  case Exchange // Microsoft Exchange
}

// Calendar information
final case class CalendarInfo(
    uid: String, // Unique identifier
    name: String, // Display name
    backend: CalendarBackend, // Backend type
    calendarType: CalendarType, // Event/Task/Memo
    color: Option[String], // Hex color (e.g., "#FF0000")
    visible: Boolean, // Whether calendar is visible
    writable: Boolean, // Whether calendar is writable
    sourceUri: Option[String] // Original source URI (for remote calendars)
)

/** Type-safe event query ADT */
sealed trait EventQuery

object EventQuery {

  /** Query for all events */
  case object All extends EventQuery

  /** Query for events containing text in a specific field */
  final case class Contains(field: String, text: String) extends EventQuery

  /** Query for events occurring within a time range */
  final case class InRange(start: Instant, end: Instant) extends EventQuery

  /** Query using raw S-expression (for advanced use cases) */
  final case class Raw(sexp: String) extends EventQuery

  // Convenience constructors for common queries
  def all: EventQuery = All
  def contains(text: String): EventQuery = Contains("summary", text)
  def contains(field: String, text: String): EventQuery = Contains(field, text)
  def inRange(start: Instant, end: Instant): EventQuery = InRange(start, end)
  def raw(sexp: String): EventQuery = Raw(sexp)

  /** Render query to S-expression format for EDS
    * @return
    *   Some(sexp) or None for All query
    */
  private[eds4s] def render(query: EventQuery): Option[String] = query match {
    case All                   => None
    case Contains(field, text) => Some(s""""contains? "$field" "$text"""")
    case InRange(start, end)   =>
      Some(
        s""""occur-in-time-range? (make-time "${start.toString}") (make-time "${end.toString}")"""
      )
    case Raw(sexp) => Some(sexp)
  }

  /** Backward compatibility: get S-expression string if present */
  extension (query: EventQuery) {
    def sexp: Option[String] = render(query)
  }
}

// Event identifier
final case class EventId(value: String) extends AnyVal

// Recurrence rule (simplified representation)
final case class RecurrenceRule(
    frequency: RecurrenceRule.Frequency,
    interval: Int = 1,
    count: Option[Int] = None,
    until: Option[Instant] = None,
    byDay: List[String] = Nil // e.g., "MO", "TU", etc.
)

object RecurrenceRule {
  enum Frequency {
    case Secondly, Minutely, Hourly, Daily, Weekly, Monthly, Yearly
  }
}

// Attendee role
enum AttendeeRole {
  case Chair // CHAIR - chairperson
  case Required // REQ-PARTICIPANT
  case Optional // OPT-PARTICIPANT
  case NonParticipant // NON-PARTICIPANT
}

// Attendee status
enum AttendeeStatus {
  case NeedsAction, Accepted, Declined, Tentative, Delegated, Completed,
    InProcess
}

// Event attendee
final case class Attendee(
    email: String,
    name: Option[String] = None,
    role: AttendeeRole = AttendeeRole.Required,
    status: AttendeeStatus = AttendeeStatus.NeedsAction,
    rsvp: Boolean = false
)

// Event organizer
final case class Organizer(
    email: String,
    name: Option[String] = None
)

// Complete calendar event (read model)
final case class Event(
    uid: String,
    summary: String,
    description: Option[String] = None,
    location: Option[String] = None,
    startTime: Instant,
    endTime: Option[Instant] = None, // None for all-day events
    isAllDay: Boolean = false,
    timeZone: Option[String] = None, // Timezone ID (e.g., "Europe/Warsaw")
    organizer: Option[Organizer] = None,
    attendees: List[Attendee] = Nil,
    recurrenceRule: Option[RecurrenceRule] = None,
    recurrenceId: Option[String] =
      None, // For modified instances of recurring events
    created: Option[Instant] = None,
    lastModified: Option[Instant] = None,
    categories: List[String] = Nil,
    url: Option[String] = None,
    status: Option[EventStatus] = None
)

enum EventStatus {
  case Confirmed, Tentative, Cancelled
}

// Event data for creation (write model)
final case class EventData(
    summary: String,
    description: Option[String] = None,
    location: Option[String] = None,
    startTime: Instant,
    endTime: Option[Instant] = None,
    isAllDay: Boolean = false,
    timeZone: Option[String] = None,
    organizer: Option[Organizer] = None,
    attendees: List[Attendee] = Nil,
    recurrenceRule: Option[RecurrenceRule] = None,
    categories: List[String] = Nil,
    url: Option[String] = None,
    status: Option[EventStatus] = None
)

// Modification type for recurring events
enum ModificationType {
  case This // Modify/delete only this instance
  case ThisAndFuture // Modify/delete this and all future instances
  case All // Modify/delete all instances (the whole series)
}
