/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus

import cats.effect.IO
import eds4s.*

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/** Stateful Mock CalendarClient for testing on CI where DBus is not available.
  * 
  * This mock actually tracks created calendars and events so tests can verify
  * the full workflow. Uses ConcurrentHashMap for thread-safe mutable state
  * that works reliably on both JVM and Scala Native.
  */
private[dbus] object MockCalendarClient extends CalendarClient[IO]:
  
  // Thread-safe mutable state using Java ConcurrentHashMap
  private val calendars = new ConcurrentHashMap[String, CalendarInfo]()
  private val events = new ConcurrentHashMap[String, java.util.List[Event]]()

  override def listCalendars: IO[List[CalendarInfo]] =
    IO(calendars.values().asScala.toList)

  override def getCalendar(uid: String): IO[Option[CalendarInfo]] =
    IO(Option(calendars.get(uid)))

  override def createCalendar(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String]
  ): IO[CalendarInfo] = IO {
    val uid = s"mock-cal-${System.nanoTime()}"
    val info = CalendarInfo(
      uid = uid,
      name = name,
      backend = backend,
      calendarType = calendarType,
      color = color,
      visible = true,
      writable = true,
      sourceUri = None
    )
    calendars.put(uid, info)
    info
  }

  override def deleteCalendar(uid: String): IO[Unit] = IO {
    calendars.remove(uid)
    events.remove(uid): Unit
  }

  override def getEvents(
      calendarUid: String,
      query: EventQuery
  ): IO[List[Event]] = IO {
    val eventList = events.get(calendarUid)
    if eventList == null then Nil else eventList.asScala.toList
  }

  override def getEvent(
      calendarUid: String,
      eventId: EventId
  ): IO[Option[Event]] = IO {
    val eventList = events.get(calendarUid)
    if eventList == null then None
    else eventList.asScala.find(_.uid == eventId.value)
  }

  override def createEvent(calendarUid: String, eventData: EventData): IO[EventId] = IO {
    val uid = s"mock-evt-${System.nanoTime()}"
    val event = Event(
      uid = uid,
      summary = eventData.summary,
      description = eventData.description,
      location = eventData.location,
      startTime = eventData.startTime,
      endTime = eventData.endTime,
      isAllDay = eventData.isAllDay,
      timeZone = eventData.timeZone
    )
    
    val eventList = events.computeIfAbsent(calendarUid, _ => new java.util.ArrayList[Event]())
    eventList.synchronized {
      eventList.add(event)
    }
    EventId(uid)
  }

  override def modifyEvent(
      calendarUid: String,
      event: Event,
      modType: ModificationType
  ): IO[Unit] = IO {
    val eventList = events.get(calendarUid)
    if eventList != null then
      eventList.synchronized {
        val idx = eventList.asScala.indexWhere(_.uid == event.uid)
        if idx >= 0 then eventList.set(idx, event): Unit
      }
  }

  override def deleteEvent(
      calendarUid: String,
      eventId: EventId,
      modType: ModificationType
  ): IO[Unit] = IO {
    val eventList = events.get(calendarUid)
    if eventList != null then
      eventList.synchronized {
        eventList.removeIf(_.uid == eventId.value): Unit
      }
  }

  override def closeCalendar(calendarUid: String): IO[Unit] = IO.unit
