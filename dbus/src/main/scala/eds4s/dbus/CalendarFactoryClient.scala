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

import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*

/** Calendar proxy representing an opened calendar */
private[eds4s] final case class CalendarProxy(
    objectPath: String,
    busName: String
)

/** Client for EDS Calendar Factory */
private[eds4s] trait CalendarFactoryClient[F[_]] {
  def openCalendar(uid: String): F[CalendarProxy]

  /** Close a previously opened calendar and release its resources.
    *
    * @param uid
    *   The calendar UID to close
    */
  def closeCalendar(uid: String): F[Unit]
}

object CalendarFactoryClient {
  def apply[F[_]: Async](
      conn: DBusConnection[F],
      opsClient: CalendarOperationsClient[F]
  ): F[CalendarFactoryClient[F]] =
    Ref[F].of(Option.empty[CalendarFactory]).flatMap { factoryRef =>
      Ref[F].of(Map.empty[String, CalendarProxy]).map { proxyCacheRef =>
        new LiveCalendarFactoryClient[F](
          conn,
          factoryRef,
          proxyCacheRef,
          opsClient
        )
      }
    }
}

private final class LiveCalendarFactoryClient[F[_]: Async](
    conn: DBusConnection[F],
    factoryRef: Ref[F, Option[CalendarFactory]],
    proxyCacheRef: Ref[F, Map[String, CalendarProxy]],
    calendarOpsClient: CalendarOperationsClient[F]
) extends CalendarFactoryClient[F] {

  private def getFactory: F[CalendarFactory] =
    factoryRef.get.flatMap {
      case Some(f) => Async[F].pure(f)
      case None    =>
        conn
          .getRemoteObject(
            EDSServices.CalendarBusName,
            EDSServices.CalendarFactoryPath,
            classOf[CalendarFactory]
          )
          .flatMap { factory =>
            factoryRef.set(Some(factory)).as(factory)
          }
    }

  override def openCalendar(uid: String): F[CalendarProxy] =
    proxyCacheRef.get.flatMap { cache =>
      cache.get(uid) match {
        case Some(proxy) => Async[F].pure(proxy)
        case None        =>
          for factory <- getFactory
          // result is CalendarProxyTuple(objectPath: String, busName: String)
          result <- Async[F].blocking(factory.OpenCalendar(uid))
          objectPath = result.objectPath
          busName = result.busName
          proxy = CalendarProxy(objectPath, busName)
          _ <- proxyCacheRef.update(_ + (uid -> proxy))
          yield proxy
      }
    }

  override def closeCalendar(uid: String): F[Unit] =
    proxyCacheRef.get.flatMap(_.get(uid) match {
      case Some(proxy) =>
        calendarOpsClient.close(proxy) *> proxyCacheRef.update(_ - uid)
      case None => Async[F].unit
    })
}
