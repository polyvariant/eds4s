# EDS4S - Evolution Data Server for Scala

A Scala 3 library for GNOME Evolution Data Server via DBus.

## What is it?

EDS4S is a type-safe, functional Scala 3 library for managing calendars and events on Linux systems. It communicates with GNOME's Evolution Data Server through DBus, giving your Scala apps native calendar integration.

## Purpose

Type-safe calendar and event management for Scala applications running on Linux desktops. Perfect for building scheduling apps, calendar tools, or any software that needs to read or write calendar data.

## Prerequisites

### JVM
- Java 17+, Scala 3.3.x, sbt 1.9.x
- Evolution Data Server (installed and running)

```bash
# Ubuntu/Debian
sudo apt install evolution-data-server
# Fedora  
sudo dnf install evolution-data-server
# Arch Linux
sudo pacman -S evolution-data-server
```

### Scala Native
- Scala Native 0.5.x, sbt 1.9.x
- Evolution Data Server (same as JVM)
- Native toolchain: clang, lld, libunwind-dev, libgc-dev
- libdbus-1 development files (for DBus FFI bindings)

```bash
# Ubuntu/Debian
sudo apt install evolution-data-server libdbus-1-dev clang lld libunwind-dev libgc-dev

# Fedora  
sudo dnf install evolution-data-server dbus-devel clang lld libunwind-devel libgc-devel

# Arch Linux
sudo pacman -S evolution-data-server dbus clang lld libunwind libgc
```

## Installation

```scala
// For JVM
libraryDependencies += "org.polyvariant" %% "eds4s-core" % "0.1.0"
libraryDependencies += "org.polyvariant" %% "eds4s-dbus" % "0.1.0"

// For Scala Native
libraryDependencies += "org.polyvariant" %%% "eds4s-core" % "0.1.0"
libraryDependencies += "org.polyvariant" %%% "eds4s-dbus" % "0.1.0"
```

## Quick Start

```scala
import eds4s.*, eds4s.dbus.DBusCalendarClient
import cats.effect.*, cats.syntax.all.*
import java.time.Instant

object QuickStart extends IOApp.Simple {
  def run: IO[Unit] = 
    DBusCalendarClient.resource[IO].use { client =>
      for
        calendars <- client.listCalendars
        _ <- IO.println(s"Found ${calendars.size} calendars")
        
        calendar <- client.createCalendar(
          name = "My Calendar", backend = CalendarBackend.Local,
          calendarType = CalendarType.Events, color = Some("#3465A4"))
        
        now <- IO(Instant.now())
        eventId <- client.createEvent(calendar.uid, EventData(
          summary = "Team Meeting", description = Some("Weekly sync"),
          location = Some("Room A"), startTime = now.plusSeconds(3600),
          endTime = Some(now.plusSeconds(7200)), timeZone = Some("Europe/Warsaw")))
        
        events <- client.getEvents(calendar.uid)
        _ <- IO.println(s"Events: ${events.map(_.summary).mkString(", ")}")
        
        _ <- client.deleteEvent(calendar.uid, eventId)
        _ <- client.deleteCalendar(calendar.uid)
      yield ()
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

## Examples

```bash
sbt "examples/runMain eds4s.examples.QuickStart"
```

## Building & Testing

```bash
sbt compile                    # Compile all modules
sbt coreJVM/test               # Core JVM tests (no EDS required)
sbt coreNative/test            # Core Native tests (no EDS required)  
sbt dbusJVM/test               # DBus JVM integration tests (requires EDS)
sbt dbusNative/test            # DBus Native tests (requires EDS + libdbus)
```

## Scala Native Support

EDS4S supports both JVM and Scala Native platforms:

- **core**: Cross-compiled for JVM and Native
  - JVM uses ical4j for ICS parsing
  - Native uses a pure Scala ICS parser

- **dbus**: Cross-compiled for JVM and Native
  - JVM uses dbus-java library
  - Native uses libdbus-1 FFI bindings

### Native Dependencies

On Debian/Ubuntu:
```bash
sudo apt install libdbus-1-dev clang lld libunwind-dev libgc-dev
```

On Fedora:
```bash
sudo dnf install dbus-devel clang lld libunwind-devel libgc-devel
```

On Arch Linux:
```bash
sudo pacman -S dbus clang lld libunwind libgc
```
## License

Apache 2.0
