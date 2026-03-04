# EDS4S - Evolution Data Server for Scala

A Scala 3 library for GNOME Evolution Data Server via DBus.

## What is it?

EDS4S is a type-safe, functional Scala 3 library for managing calendars and events on Linux systems. It communicates with GNOME's Evolution Data Server through DBus, giving your Scala apps native calendar integration.

## Purpose

Type-safe calendar and event management for Scala applications running on Linux desktops. Perfect for building scheduling apps, calendar tools, or any software that needs to read or write calendar data.

## Prerequisites

- Java 17+, Scala 3.3.x, sbt 1.9.x
- Evolution Data Server (installed and running, default on systems utilizing Gnome)

```bash
# Ubuntu/Debian
sudo apt install evolution-data-server
# Fedora  
sudo dnf install evolution-data-server
# Arch Linux
sudo pacman -S evolution-data-server
```

## Installation

```scala
libraryDependencies += "org.polyvariant" %% "eds4s-core" % "0.1.0"
libraryDependencies += "org.polyvariant" %% "eds4s-dbus" % "0.1.0"
```

## Quick Start

```scala
import eds4s.*, eds4s.dbus.DBusCalendarClient
import cats.effect.*, cats.syntax.all.*
import java.time.Instant

object QuickStart extends IOApp.Simple {
  def run: IO[Unit] = 
    DBusCalendarClient.resource[IO].use { client =>
      for {
        calendars <- client.listCalendars
        _ <- IO.println(s"Found ${calendars.size} calendars")
        
        calendar <- client.createCalendar(
          name = "My Calendar", backend = CalendarBackend.Local,
          calendarType = CalendarType.Events, color = Some("#3465A4"))
        
        now <- IO(Instant.now())
        eventId <- client.createEvent(calendar.uid, EventData(
          summary = "Team Meeting", description = Some("Weekly sync"),
          location = Some("Room A"), startTime = now.plusSeconds(3600),
          endTime = Some(now.plusSeconds(7200)), timeZone = Some("Europe/Warsaw"))
        )

        events <- client.getEvents(calendar.uid)
        _ <- IO.println(s"Events: ${events.map(_.summary).mkString(", ")}")
        _ <- client.deleteEvent(calendar.uid, eventId)
        _ <- client.deleteCalendar(calendar.uid)
      } yield ()
    }
}
```

## Common Operations

### List Calendars
```scala
calendars <- client.listCalendars
calendars.foreach(c => println(s"${c.name} (${c.uid})"))
```

### Create Calendar
```scala
calendar <- client.createCalendar(
  name = "Work", backend = CalendarBackend.Local,
  calendarType = CalendarType.Events, color = Some("#FF5500"))
```

### Create Event
```scala
eventId <- client.createEvent(calendarUid, EventData(
  summary = "Meeting",
  startTime = Instant.parse("2024-03-15T10:00:00Z"),
  endTime = Some(Instant.parse("2024-03-15T11:00:00Z"))))
```

### Query Events
```scala
// All events
events <- client.getEvents(calendarUid, EventQuery.all)

// By text search
events <- client.getEvents(calendarUid, EventQuery.contains("meeting"))

// By time range
events <- client.getEvents(calendarUid, 
  EventQuery.inRange(Instant.parse("2024-03-01T00:00:00Z"),
                     Instant.parse("2024-03-31T23:59:59Z")))
```

### Delete Event/Calendar
```scala
client.deleteEvent(calendarUid, eventId, ModificationType.This)
client.deleteCalendar(calendarUid)
```

## API Overview

`CalendarClient[F[_]]` is the main interface:
- **Calendar ops**: `listCalendars`, `getCalendar`, `createCalendar`, `deleteCalendar`
- **Event ops**: `getEvents`, `getEvent`, `createEvent`, `modifyEvent`, `deleteEvent`

Key types: `CalendarInfo`, `Event`, `EventData`, `EventQuery`, `EventId`

