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

package eds4s.dbus

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import java.util.{List => JList, Map => JMap}
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.UInt32

/** EDS DBus service and interface names */
object EDSServices {
  // Source Registry Service
  val SourcesBusName = "org.gnome.evolution.dataserver.Sources5"
  val SourceManagerPath = "/org/gnome/evolution/dataserver/SourceManager"
  val SourceManagerInterface = "org.gnome.evolution.dataserver.SourceManager"

  // Calendar Factory Service
  val CalendarBusName = "org.gnome.evolution.dataserver.Calendar8"
  val CalendarFactoryPath = "/org/gnome/evolution/dataserver/CalendarFactory"
  val CalendarFactoryInterface =
    "org.gnome.evolution.dataserver.CalendarFactory"

  // Calendar Operations Interface
  val CalendarInterface = "org.gnome.evolution.dataserver.Calendar"

  // Calendar View Interface (for live updates)
  val CalendarViewInterface = "org.gnome.evolution.dataserver.CalendarView"

  // Source Interface (for individual sources)
  val SourceInterface = "org.gnome.evolution.dataserver.Source"
}

/** ObjectManager interface for listing managed objects */
@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
trait ObjectManager extends DBusInterface {

  /** Get all managed objects with their interfaces and properties Returns:
    * a{oa{sa{sv}}} - Map[object_path, Map[interface_name, Map[property_name,
    * variant]]]
    */
  def GetManagedObjects(): JMap[DBusPath, JMap[String, JMap[String, AnyRef]]]
}

/** Properties interface for getting/setting properties */
@DBusInterfaceName("org.freedesktop.DBus.Properties")
trait Properties extends DBusInterface {

  /** Get a property value */
  def Get(interfaceName: String, propertyName: String): AnyRef

  /** Get all properties for an interface */
  def GetAll(interfaceName: String): JMap[String, AnyRef]

  /** Set a property value */
  def Set(interfaceName: String, propertyName: String, value: AnyRef): Unit
}

/** Source Manager DBus interface */
@DBusInterfaceName("org.gnome.evolution.dataserver.SourceManager")
trait SourceManager extends DBusInterface {

  /** Create sources from key-value data
    * @param data
    *   a{ss} - Map of key-value pairs (INI-style source configuration)
    */
  def CreateSources(data: JMap[String, String]): Unit

  /** Reload all sources */
  def Reload(): Unit

  /** Refresh backend for a specific source
    * @param sourceUid
    *   The UID of the source to refresh
    */
  def RefreshBackend(sourceUid: String): Unit
}

/** Source DBus interface for individual sources Properties accessible via
  * org.freedesktop.DBus.Properties:
  *   - UID: String (read-only)
  *   - Data: String (INI-style source configuration)
  *   - ConnectionStatus: String (connection status)
  */
@DBusInterfaceName("org.gnome.evolution.dataserver.Source")
trait Source extends DBusInterface

/** Calendar Factory DBus interface for opening calendars */
@DBusInterfaceName("org.gnome.evolution.dataserver.CalendarFactory")
trait CalendarFactory extends DBusInterface {

  /** Open a calendar by source UID
    * @param sourceUid
    *   The source UID to open
    * @return
    *   CalendarProxyTuple(object_path, bus_name)
    */
  def OpenCalendar(sourceUid: String): CalendarProxyTuple

  /** Open a task list by source UID
    * @param sourceUid
    *   The source UID to open
    * @return
    *   CalendarProxyTuple(object_path, bus_name)
    */
  def OpenTaskList(sourceUid: String): CalendarProxyTuple

  /** Open a memo list by source UID
    * @param sourceUid
    *   The source UID to open
    * @return
    *   CalendarProxyTuple(object_path, bus_name)
    */
  def OpenMemoList(sourceUid: String): CalendarProxyTuple
}

/** DBus Tuple for calendar proxy info (object_path: s, bus_name: s) Extends
  * Tuple for proper multiple return value handling in dbus-java 5.x
  */
final case class CalendarProxyTuple(
    @Position(0) objectPath: String,
    @Position(1) busName: String
) extends Tuple

final case class CalendarProxyInfo(
    @Position(0) objectPath: String,
    @Position(1) busName: String
) extends Struct

/** Calendar Operations DBus interface */
@DBusInterfaceName("org.gnome.evolution.dataserver.Calendar")
trait CalendarOperations extends DBusInterface {

  /** Open the calendar backend
    * @return
    *   List of supported property names
    */
  def Open(): JList[String]

  /** Close the calendar backend */
  def Close(): Unit

  /** Refresh calendar data from backend */
  def Refresh(): Unit

  /** Get a single calendar object by UID and recurrence ID
    * @param uid
    *   The object UID
    * @param rid
    *   The recurrence ID (empty string for non-recurring)
    * @return
    *   ICS string representation of the object
    */
  def GetObject(uid: String, rid: String): String

  /** Get all calendar objects matching a query
    * @param query
    *   S-expression query string
    * @return
    *   List of ICS strings
    */
  def GetObjectList(query: String): JList[String]

  /** Create calendar objects from ICS strings
    * @param icsObjects
    *   List of ICS strings to create
    * @param opflags
    *   Operation flags
    * @return
    *   List of created object UIDs
    */
  def CreateObjects(icsObjects: JList[String], opflags: UInt32): JList[String]

  /** Modify calendar objects from ICS strings
    * @param icsObjects
    *   List of ICS strings with modifications
    * @param modType
    *   Modification type (This, ThisAndFuture, All)
    * @param opflags
    *   Operation flags
    */
  def ModifyObjects(
      icsObjects: JList[String],
      modType: String,
      opflags: UInt32
  ): Unit

  /** Remove calendar objects by UID/rid pairs
    * @param uidRidArray
    *   Array of (uid, rid) pairs
    * @param modType
    *   Modification type (This, ThisAndFuture, All)
    * @param opflags
    *   Operation flags
    */
  def RemoveObjects(
      uidRidArray: JList[UidRidPair],
      modType: String,
      opflags: UInt32
  ): Unit

  /** Get a timezone by ID
    * @param tzId
    *   Timezone identifier (e.g., "America/New_York")
    * @return
    *   VTIMEZONE ICS string
    */
  def GetTimezone(tzId: String): String

  /** Add a timezone to the calendar
    * @param tzObject
    *   VTIMEZONE ICS string
    */
  def AddTimezone(tzObject: String): Unit

  /** Get a live view for a query
    * @param query
    *   S-expression query string
    * @return
    *   Object path of the view
    */
  def GetView(query: String): String
}

/** DBus struct for UID/rid pair (a(ss) in DBus signature) */
final class UidRidPair(uid: String, rid: String) extends Struct {
  @Position(0)
  private val _uid: String = uid

  @Position(1)
  private val _rid: String = rid

  def getUid: String = _uid
  def getRid: String = _rid
}

/** Alias for backward compatibility */
type EventIdPair = UidRidPair

/** Companion object for EventIdPair factory */
object EventIdPair {
  def apply(uid: String, rid: String): EventIdPair = new UidRidPair(uid, rid)
}

/** Operation flags for calendar operations */
object OperationFlags {

  /** No special flags */
  val None: UInt32 = UInt32(0L)

  /** Send to all attendees */
  val SendToAll: UInt32 = UInt32(1L)

  /** Send only to organizer */
  val SendOnlyToOrganizer: UInt32 = UInt32(2L)

  /** Ensure UID is generated if missing */
  val EnsureUID: UInt32 = UInt32(4L)

  /** Generate instances for recurring events */
  val GenerateInstances: UInt32 = UInt32(8L)
}
