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

package eds4s.dbus

import cats.effect.Sync
import cats.syntax.all.*
import eds4s.{CalendarInfo, CalendarBackend, CalendarType, EdsError}
import eds4s.EdsError.*
import scala.jdk.CollectionConverters.*
import org.freedesktop.dbus.DBusPath
import java.util.{HashMap => JHashMap, Map => JMap}

/** Client for EDS Source Registry using actual DBus API */
private[eds4s] trait SourceRegistryClient[F[_]] {
  def listSources: F[List[CalendarInfo]]
  def createSource(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType
  ): F[String]

  /** Remove a calendar source from the registry.
    *
    * '''Note:''' This method is currently not implemented.
    *
    * The EDS DBus API does not provide a direct method for source removal.
    * Sources are managed via file system operations on the EDS configuration
    * directory (typically `~/.config/evolution/sources/`).
    *
    * @param uid
    *   The UID of the source to remove
    * @return
    *   Always raises [[EdsError.UnsupportedOperation]]
    * @throws EdsError.UnsupportedOperation
    *   Always, as this is not yet implemented
    */
  def removeSource(uid: String): F[Unit]
}

object SourceRegistryClient {
  def apply[F[_]: Sync](conn: DBusConnection[F]): SourceRegistryClient[F] =
    new LiveSourceRegistryClient[F](conn)
}

private class LiveSourceRegistryClient[F[_]: Sync](conn: DBusConnection[F])
    extends SourceRegistryClient[F] {

  override def listSources: F[List[CalendarInfo]] =
    for objectManager <- conn.getRemoteObject(
      EDSServices.SourcesBusName,
      EDSServices.SourceManagerPath,
      classOf[ObjectManager]
    )
    managedObjects <- Sync[F].blocking(objectManager.GetManagedObjects())
    yield
      // objects is Map[DBusPath, Map[String, Map[String, AnyRef]]]
      // where: path -> interface -> property -> value
      managedObjects.asScala.toList.flatMap { (path, interfaces) =>
        // Look for the Source interface
        Option(interfaces.get(EDSServices.SourceInterface)).map { props =>
          val uidRaw = Option(props.get("UID")).map(_.toString).getOrElse("")
          val uid =
            if uidRaw.startsWith("[") && uidRaw.endsWith("]") then uidRaw
              .substring(1, uidRaw.length - 1)
            else
              uidRaw
          val dataStr = Option(props.get("Data")).map(_.toString).getOrElse("")
          val parsed = parseIniData(dataStr)

          // Check if this is a calendar source (has Calendar extension)
          val hasCalendar = Option(
            interfaces.get(EDSServices.SourceInterface + ".Calendar")
          ).isDefined

          CalendarInfo(
            uid = uid,
            name = parsed.getOrElse("DisplayName", uid),
            backend = parsed
              .get("BackendName")
              .map(parseBackend)
              .getOrElse(CalendarBackend.Local),
            calendarType = CalendarType.Events, // Default
            color = parsed.get("Color"),
            visible = parsed.getOrElse("Enabled", "true") == "true",
            writable = true, // Would need to check Writable interface
            sourceUri = parsed.get("Uri")
          )
        }
      }

  override def createSource(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType
  ): F[String] =
    for manager <- conn.getRemoteObject(
      EDSServices.SourcesBusName,
      EDSServices.SourceManagerPath,
      classOf[SourceManager]
    )
    // Generate unique UID
    uid = java.util.UUID.randomUUID().toString
    // Build INI-style source configuration
    // The key is the UID, the value is the full INI content
    iniContent = buildIniContent(name, backend, calendarType)
    sourceConfig = new JHashMap[String, String]()
    _ = sourceConfig.put(uid, iniContent)
    _ <- Sync[F].blocking(manager.CreateSources(sourceConfig))
    yield uid

  override def removeSource(uid: String): F[Unit] =
    Sync[F].raiseError(
      EdsError.UnsupportedOperation(
        "RemoveSource",
        Some(
          "EDS DBus API does not support source removal - use file system directly"
        )
      )
    )

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
      case "local"  => CalendarBackend.Local
      case "google" => CalendarBackend.Google
      case "caldav" => CalendarBackend.Caldav
      case "webcal" => CalendarBackend.WebCal
      case _        => CalendarBackend.Local
    }

  /** Build INI-style source configuration content for CreateSources The format
    * follows the .source file format with sections
    */
  private def buildIniContent(
      name: String,
      backend: CalendarBackend,
      calendarType: CalendarType
  ): String = {
    val backendName = backend match {
      case CalendarBackend.Local  => "local"
      case CalendarBackend.Google => "google"
      case CalendarBackend.Caldav => "caldav"
      case CalendarBackend.WebCal => "webcal"
      case _                      => "local"
    }

    // Build INI content with proper sections
    val sb = new StringBuilder()
    sb.append("[Data Source]\n")
    sb.append(s"DisplayName=$name\n")
    sb.append("Enabled=true\n")
    sb.append("\n")
    sb.append("[Calendar]\n")
    sb.append(s"BackendName=$backendName\n")
    sb.append("Color=#FF5500\n")
    sb.append("Selected=true\n")
    sb.append("Order=0\n")
    sb.append("\n")
    sb.append("[Offline]\n")
    sb.append("StaySynchronized=true\n")
    sb.toString()
  }
}
