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
import eds4s.EdsError
import eds4s.EdsError.*
import eds4s.ModificationType
import java.util.ArrayList
import scala.jdk.CollectionConverters.*

/** Client for calendar operations */
private[eds4s] trait CalendarOperationsClient[F[_]] {
  def getObject(proxy: CalendarProxy, uid: String): F[String]
  def getObjectList(proxy: CalendarProxy, sexp: String): F[List[String]]
  def createObjects(
      proxy: CalendarProxy,
      icsStrings: List[String]
  ): F[List[String]]
  def removeObjects(
      proxy: CalendarProxy,
      uids: List[String],
      modType: ModificationType
  ): F[Unit]

  /** Close the calendar backend for this proxy and remove from cache.
    *
    * @param proxy
    *   The calendar proxy to close
    */
  def close(proxy: CalendarProxy): F[Unit]
}

object CalendarOperationsClient {
  def apply[F[_]: Async](
      conn: DBusConnection[F]
  ): F[CalendarOperationsClient[F]] =
    Ref[F].of(Map.empty[String, CalendarOperations]).map { opsCacheRef =>
      new LiveCalendarOperationsClient[F](conn, opsCacheRef)
    }
}

private final class LiveCalendarOperationsClient[F[_]: Async](
    conn: DBusConnection[F],
    opsCacheRef: Ref[F, Map[String, CalendarOperations]]
) extends CalendarOperationsClient[F] {

  // Get or create operations for a proxy
  private def getOps(proxy: CalendarProxy): F[CalendarOperations] =
    opsCacheRef.get.flatMap { cache =>
      cache.get(proxy.objectPath) match {
        case Some(ops) => Async[F].pure(ops)
        case None      =>
          // Not cached, fetch and cache it
          conn
            .getRemoteObject(
              proxy.busName,
              proxy.objectPath,
              classOf[CalendarOperations]
            )
            .flatMap { ops =>
              // Call Open() to initialize the calendar backend
              Async[F].blocking(ops.Open()).flatMap { _ =>
                opsCacheRef.update(_ + (proxy.objectPath -> ops)).as(ops)
              }
            }
      }
    }

  override def getObject(proxy: CalendarProxy, uid: String): F[String] =
    for op <- getOps(proxy)
    ics <- Async[F].blocking(op.GetObject(uid, ""))
    yield ics

  override def getObjectList(
      proxy: CalendarProxy,
      sexp: String
  ): F[List[String]] =
    for op <- getOps(proxy)
    icsList <- Async[F].blocking(op.GetObjectList(sexp))
    yield icsList.asScala.toList

  override def createObjects(
      proxy: CalendarProxy,
      icsStrings: List[String]
  ): F[List[String]] =
    for op <- getOps(proxy)
    jlist = {
      val l = new ArrayList[String]()
      icsStrings.foreach(l.add)
      l
    }
    uids <- Async[F].blocking(op.CreateObjects(jlist, OperationFlags.None))
    yield uids.asScala.toList

  override def removeObjects(
      proxy: CalendarProxy,
      uids: List[String],
      modType: ModificationType
  ): F[Unit] =
    for op <- getOps(proxy)
    jlist = {
      val l = new ArrayList[UidRidPair]()
      uids.foreach(uid => l.add(UidRidPair(uid, "")))
      l
    }
    // EDS expects lowercase kebab-case nicknames for mod type
    modStr = modType match {
      case ModificationType.This          => "this"
      case ModificationType.ThisAndFuture => "this-and-future"
      case ModificationType.All           => "all"
    }
    _ <- Async[F].blocking(op.RemoveObjects(jlist, modStr, OperationFlags.None))
    yield ()

  override def close(proxy: CalendarProxy): F[Unit] =
    opsCacheRef.get.flatMap(_.get(proxy.objectPath) match {
      case Some(ops) =>
        Async[F].blocking(ops.Close()) *> opsCacheRef.update(
          _ - proxy.objectPath
        )
      case None => Async[F].unit
    })
}
