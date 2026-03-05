/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus.native

import cats.effect.{Async, Ref, Resource, Sync}
import cats.syntax.all.*
import eds4s.*
import eds4s.EdsError.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

import libdbus.*
import libdbusUtils.*

/** Native implementation of CalendarClient using libdbus-1 FFI.
  *
  * This provides a cats-effect friendly interface for communicating with
  * Evolution Data Server via DBus on Scala Native.
  *
  * Note: This is a simplified implementation that directly calls DBus methods
  * without the proxy pattern used in the JVM implementation.
  */
/** Calendar proxy representing an opened calendar */
private case class CalendarProxy(
    objectPath: String,
    busName: String
)

/** Native implementation of CalendarClient using libdbus-1 FFI.
  *
  * This provides a cats-effect friendly interface for communicating with
  * Evolution Data Server via DBus on Scala Native.
  */
final class NativeDBusCalendarClient[F[_]: Async] private (
    conn: NativeDBusConnection[F],
    icalConverter: IcalConverter[F],
    proxyCacheRef: Ref[F, Map[String, CalendarProxy]]
) extends CalendarClient[F] {

  // EDS DBus constants
  private val EDS_SOURCES_BUS_NAME = "org.gnome.evolution.dataserver.Sources5"
  private val EDS_SOURCE_MANAGER_PATH = "/org/gnome/evolution/dataserver/SourceManager"
  private val EDS_SOURCE_MANAGER_IFACE = "org.gnome.evolution.dataserver.SourceManager"
  private val EDS_CALENDAR_FACTORY_BUS_NAME = "org.gnome.evolution.dataserver.Calendar8"
  private val EDS_CALENDAR_FACTORY_PATH = "/org/gnome/evolution/dataserver/CalendarFactory"
  private val EDS_CALENDAR_FACTORY_IFACE = "org.gnome.evolution.dataserver.CalendarFactory"
  private val EDS_CALENDAR_IFACE = "org.gnome.evolution.dataserver.Calendar"

  // Counter for generating unique UIDs (since java.util.UUID is not available in Scala Native)
  private val uidCounter = new java.util.concurrent.atomic.AtomicLong(0)

  override def listCalendars: F[List[CalendarInfo]] =
    (for
      reply <- conn.callMethod(
        EDS_SOURCES_BUS_NAME,
        EDS_SOURCE_MANAGER_PATH,
        "org.freedesktop.DBus.ObjectManager",
        "GetManagedObjects"
      )
      calendars <- Async[F].blocking {
        reply.read { reader => parseManagedObjects(reader) }
      }
    yield calendars)
    .handleErrorWith { e =>
      Sync[F].raiseError(CalendarOperationFailed("listCalendars", e.getMessage))
    }

  override def getCalendar(uid: String): F[Option[CalendarInfo]] =
    listCalendars.map(_.find(_.uid == uid))

  override def createCalendar(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String]
  ): F[CalendarInfo] =
    Sync[F].delay {
      val uid = generateUid()
      val iniContent = buildIniContent(name, backend, calendarType, color)
      (uid, iniContent)
    }.flatMap { (uid, iniContent) =>
      for
        // Create the source via DBus
        _ <- conn.callMethod(
          EDS_SOURCES_BUS_NAME,
          EDS_SOURCE_MANAGER_PATH,
          EDS_SOURCE_MANAGER_IFACE,
          "CreateSources",
          builder => {
            // Create a{ss} dict with uid -> iniContent
            builder.openArray("{ss}") { dictBuilder =>
              dictBuilder.openContainer(DBUS_TYPE_DICT_ENTRY, null) { entry =>
                entry.appendString(uid).appendString(iniContent): Unit
              }
            }
          }
        )
        // Fetch the created calendar info
        info <- getCalendar(uid).flatMap {
          case Some(info) => Sync[F].pure(info.copy(color = color))
          case None       => Sync[F].raiseError(CalendarNotFound(uid))
        }
      yield info
    }
  override def deleteCalendar(uid: String): F[Unit] =
    Sync[F].raiseError(
      UnsupportedOperation(
        "deleteCalendar",
        Some("EDS DBus API does not support source removal - use file system directly")
      )
    )

  override def getEvents(calendarUid: String, query: EventQuery): F[List[Event]] =
    for
      proxy <- openCalendar(calendarUid)
      sexp = EventQuery.render(query).getOrElse("#t") // "#t" means "true" - match all events
      icsList <- getCalendarObjectList(proxy, sexp)
      events <- icsList.traverse(icalConverter.parseEvent(_)).handleErrorWith { e =>
        Async[F].raiseError(
          InvalidIcsFormat(s"Failed to parse events: ${e.getMessage}", Some(e))
        )
      }
    yield events

  override def getEvent(calendarUid: String, eventId: EventId): F[Option[Event]] =
    (for
      proxy <- openCalendar(calendarUid)
      ics <- getCalendarObject(proxy, eventId.value)
      event <- icalConverter.parseEvent(ics)
    yield Some(event))
    .handleErrorWith { _ =>
      // If event not found, return None
      Sync[F].pure(None: Option[Event])
    }

  override def createEvent(calendarUid: String, eventData: EventData): F[EventId] =
    for
      proxy <- openCalendar(calendarUid)
      ics <- icalConverter.renderEvent(eventData)
      uids <- createCalendarObjects(proxy, List(ics))
      uid <- uids.headOption match {
        case Some(uid) =>
          Sync[F].pure(EventId(uid.stripPrefix("[").stripSuffix("]")))
        case None =>
          Sync[F].raiseError(
            CalendarOperationFailed("createEvent", "No UID returned")
          )
      }
    yield uid

  override def modifyEvent(
      calendarUid: String,
      event: Event,
      modType: ModificationType
  ): F[Unit] =
    for
      proxy <- openCalendar(calendarUid)
      ics <- icalConverter.renderExistingEvent(event)
      // Use CreateObjects which typically replaces existing events with same UID
      _ <- createCalendarObjects(proxy, List(ics))
    yield ()

  override def deleteEvent(
      calendarUid: String,
      eventId: EventId,
      modType: ModificationType
  ): F[Unit] =
    for
      proxy <- openCalendar(calendarUid)
      _ <- removeCalendarObjects(proxy, List(eventId.value), modType)
    yield ()

  override def closeCalendar(calendarUid: String): F[Unit] =
    proxyCacheRef.get.flatMap(_.get(calendarUid) match {
      case Some(proxy) =>
        closeCalendarBackend(proxy) *> proxyCacheRef.update(_ - calendarUid)
      case None => Async[F].unit
    })

  // ============ Helper Methods ============

  /** Generate a unique UID using nano time and counter */
  private def generateUid(): String = {
    val timestamp = System.nanoTime()
    val counter = uidCounter.getAndIncrement()
    f"$timestamp%016x-$counter%08x"
  }

  /** Open a calendar and cache the proxy */
  private def openCalendar(uid: String): F[CalendarProxy] =
    proxyCacheRef.get.flatMap { cache =>
      cache.get(uid) match {
        case Some(proxy) => Async[F].pure(proxy)
        case None        =>
          for
            reply <- conn.callMethod(
              EDS_CALENDAR_FACTORY_BUS_NAME,
              EDS_CALENDAR_FACTORY_PATH,
              EDS_CALENDAR_FACTORY_IFACE,
              "OpenCalendar",
              _.appendString(uid)
            )
            result <- Async[F].blocking {
              reply.read { reader =>
                // Result is a struct (objectPath: s, busName: s)
                val objectPath = reader.readString()
                reader.next()
                val busName = reader.readString()
                CalendarProxy(objectPath, busName)
              }
            }
            _ <- proxyCacheRef.update(_ + (uid -> result))
            // Open the calendar backend
            _ <- openCalendarBackend(result)
          yield result
      }
    }

  /** Open the calendar backend by calling Open() on the proxy */
  private def openCalendarBackend(proxy: CalendarProxy): F[Unit] =
    conn.callMethod(
      proxy.busName,
      proxy.objectPath,
      EDS_CALENDAR_IFACE,
      "Open"
    ).void

  /** Close the calendar backend */
  private def closeCalendarBackend(proxy: CalendarProxy): F[Unit] =
    conn.callMethod(
      proxy.busName,
      proxy.objectPath,
      EDS_CALENDAR_IFACE,
      "Close"
    ).void

  /** Get a list of calendar objects matching a query */
  private def getCalendarObjectList(proxy: CalendarProxy, sexp: String): F[List[String]] =
    for
      reply <- conn.callMethod(
        proxy.busName,
        proxy.objectPath,
        EDS_CALENDAR_IFACE,
        "GetObjectList",
        _.appendString(sexp)
      )
      result <- Async[F].blocking {
        reply.read { reader => reader.readStringArray().toList }
      }
    yield result

  /** Get a single calendar object by UID */
  private def getCalendarObject(proxy: CalendarProxy, uid: String): F[String] =
    for
      reply <- conn.callMethod(
        proxy.busName,
        proxy.objectPath,
        EDS_CALENDAR_IFACE,
        "GetObject",
        builder => builder.appendString(uid).appendString("") // uid, rid (empty for non-recurring)
      )
      result <- Async[F].blocking {
        reply.read { reader => reader.readString() }
      }
    yield result

  /** Create calendar objects from ICS strings */
  private def createCalendarObjects(proxy: CalendarProxy, icsList: List[String]): F[List[String]] =
    for
      reply <- conn.callMethod(
        proxy.busName,
        proxy.objectPath,
        EDS_CALENDAR_IFACE,
        "CreateObjects",
        builder => {
          builder.appendStringArray(icsList)
          builder.appendUInt32(0.toUInt) // OperationFlags.None
        }
      )
      result <- Async[F].blocking {
        reply.read { reader => reader.readStringArray().toList }
      }
    yield result

  /** Remove calendar objects by UID */
  private def removeCalendarObjects(
      proxy: CalendarProxy,
      uids: List[String],
      modType: ModificationType
  ): F[Unit] = {
    val modStr = modType match {
      case ModificationType.This          => "this"
      case ModificationType.ThisAndFuture => "this-and-future"
      case ModificationType.All           => "all"
    }
    conn.callMethod(
      proxy.busName,
      proxy.objectPath,
      EDS_CALENDAR_IFACE,
      "RemoveObjects",
      builder => {
        // a(ss) - array of (uid, rid) pairs
        builder.openArray("(ss)") { arrBuilder =>
          uids.foreach { uid =>
            arrBuilder.openContainer(DBUS_TYPE_STRUCT, null) { structBuilder =>
              structBuilder.appendString(uid).appendString("")
            }
          }
        }
        builder.appendString(modStr)
        builder.appendUInt32(0.toUInt) // OperationFlags.None
      }
    ).void
  }

  /** Build INI-style source configuration */
  private def buildIniContent(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType,
      color: Option[String]
  ): String = {
    val backendName = backend match {
      case CalendarBackend.Local    => "local"
      case CalendarBackend.Google   => "google"
      case CalendarBackend.Caldav   => "caldav"
      case CalendarBackend.WebCal   => "webcal"
      case CalendarBackend.Exchange => "exchange"
    }

    val colorStr = color.getOrElse("#FF5500")

    val sb = new StringBuilder()
    sb.append("[Data Source]\n")
    sb.append(s"DisplayName=$name\n")
    sb.append("Enabled=true\n")
    sb.append("\n")
    sb.append("[Calendar]\n")
    sb.append(s"BackendName=$backendName\n")
    sb.append(s"Color=$colorStr\n")
    sb.append("Selected=true\n")
    sb.append("Order=0\n")
    sb.append("\n")
    sb.append("[Offline]\n")
    sb.append("StaySynchronized=true\n")
    sb.toString()
  }

  private def parseManagedObjects(reader: DBusReplyReader): List[CalendarInfo] = {
    // The result is a{oa{sa{sv}}}
    // Array of object paths -> dict of interface names -> dict of property names/values
    import scala.collection.mutable.ListBuffer
    val calendars = ListBuffer[CalendarInfo]()

    // Helper class to collect properties
    class SourceBuilder {
      var uid = ""
      var displayName = ""
      var data = ""
      var enabled = true
      
      def reset(): Unit = {
        uid = ""
        displayName = ""
        data = ""
        enabled = true
      }
      
      def build(): Option[CalendarInfo] = {
        if uid.nonEmpty && displayName.nonEmpty then
          val parsedData = parseIniData(data)
          Some(CalendarInfo(
            uid = uid,
            name = displayName,
            backend = parsedData.get("BackendName").map(parseBackend).getOrElse(CalendarBackend.Local),
            calendarType = CalendarType.Events,
            color = parsedData.get("Color"),
            visible = enabled,
            writable = true,
            sourceUri = parsedData.get("Uri")
          ))
        else None
      }
    }
    
    val sourceBuilder = new SourceBuilder

    // Enter the outer array
    reader.forEachElement { objEntry =>
      objEntry.recurse { inner =>
        if inner.argType == DBUS_TYPE_OBJECT_PATH then
          inner.readObjectPath() // consume path
          inner.next()
          
          sourceBuilder.reset()
          
          if inner.argType == DBUS_TYPE_ARRAY then
            inner.recurse { ifaceEntry =>
              if ifaceEntry.argType == DBUS_TYPE_STRING then
                val ifaceName = ifaceEntry.readString()
                ifaceEntry.next()
                
                if ifaceName.contains("Source") && ifaceEntry.argType == DBUS_TYPE_ARRAY then
                  ifaceEntry.recurse { propEntry =>
                    if propEntry.argType == DBUS_TYPE_STRING then
                      val propName = propEntry.readString()
                      propEntry.next()
                      if propEntry.argType == DBUS_TYPE_VARIANT then
                        propEntry.recurse { vReader =>
                          val value = vReader.readString()
                          propName match
                            case "UID" => sourceBuilder.uid = if value.startsWith("[") && value.endsWith("]") then value.substring(1, value.length - 1) else value
                            case "DisplayName" => sourceBuilder.displayName = value
                            case "Data" => sourceBuilder.data = value
                            case "Enabled" => sourceBuilder.enabled = value == "true"
                            case _ => // skip
                        }
                  }
                  sourceBuilder.build().foreach(calendars += _)
            }
      }
    }

    calendars.result()
  }

  /** Parse INI-style data string into key-value map */
  private def parseIniData(data: String): Map[String, String] =
    data.linesIterator
      .filter(_.contains("="))
      .map { line =>
        val idx = line.indexOf('=')
        line.substring(0, idx).trim -> line.substring(idx + 1).trim
      }
      .toMap

  private def parseBackend(name: String): CalendarBackend =
    name.toLowerCase match {
      case "local"    => CalendarBackend.Local
      case "google"   => CalendarBackend.Google
      case "caldav"   => CalendarBackend.Caldav
      case "webcal"   => CalendarBackend.WebCal
      case "exchange" => CalendarBackend.Exchange
      case _           => CalendarBackend.Local
    }
}

object NativeDBusCalendarClient {

  /** Create a NativeDBusCalendarClient using a session bus connection */
  def resource[F[_]: Async]: Resource[F, CalendarClient[F]] =
    for
      conn <- NativeDBusConnection.sessionBus[F]
      client <- Resource.eval(createClient(conn))
    yield client

  /** Create a NativeDBusCalendarClient from an existing connection */
  def apply[F[_]: Async](conn: NativeDBusConnection[F]): F[CalendarClient[F]] =
    createClient(conn)

  private def createClient[F[_]: Async](
      conn: NativeDBusConnection[F]
  ): F[CalendarClient[F]] =
    for proxyCacheRef <- Ref[F].of(Map.empty[String, CalendarProxy])
    yield new NativeDBusCalendarClient[F](conn, IcalConverter[F], proxyCacheRef)
}
