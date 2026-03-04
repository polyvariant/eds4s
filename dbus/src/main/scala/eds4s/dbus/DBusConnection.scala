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

import cats.effect.*
import cats.syntax.all.*

import org.freedesktop.dbus.connections.impl.DBusConnection as JDBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.exceptions.DBusException

import eds4s.EdsError
import eds4s.EdsError.*

trait DBusConnection[F[_]] {

  /** Get a remote object proxy for the specified bus name and object path */
  def getRemoteObject[T <: DBusInterface](
      busName: String,
      objectPath: String,
      ifaceClass: Class[T]
  ): F[T]

  /** Check if the connection is still valid */
  def isConnected: F[Boolean]

  /** Close the connection */
  def close: F[Unit]
}

object DBusConnection {

  /** Create a live DBus connection to the session bus using Resource for safe
    * lifecycle management
    */
  def resource[F[_]: Async]: Resource[F, DBusConnection[F]] =
    Resource.make(
      Async[F].blocking {
        val conn = DBusConnectionBuilder.forSessionBus().build()
        LiveDBusConnection[F](conn)
      }
    )(_.close)

  /** Create a live DBus connection (unmanaged - caller responsible for closing)
    */
  def live[F[_]: Async]: F[DBusConnection[F]] =
    Async[F].blocking {
      val conn = DBusConnectionBuilder.forSessionBus().build()
      LiveDBusConnection[F](conn)
    }
}

/** Live implementation wrapping dbus-java connection */
private final case class LiveDBusConnection[F[_]: Async](
    underlying: JDBusConnection
) extends DBusConnection[F] {

  override def getRemoteObject[T <: DBusInterface](
      busName: String,
      objectPath: String,
      ifaceClass: Class[T]
  ): F[T] =
    Async[F]
      .blocking {
        underlying.getRemoteObject(busName, objectPath, ifaceClass)
      }
      .adaptError {
        case e: DBusException => DBusConnectionFailed(e)
        case e                => DBusConnectionFailed(e)
      }

  override def isConnected: F[Boolean] =
    Async[F].blocking(underlying.isConnected)

  override def close: F[Unit] =
    Async[F].blocking(underlying.close())
}
