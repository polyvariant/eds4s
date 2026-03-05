/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus.native

import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

import libdbus.*
import libdbusUtils.*

/** High-level DBus connection wrapper for Scala Native.
  *
  * Provides a cats-effect friendly interface to libdbus-1 for
  * connecting to the session/system bus and making method calls.
  */
trait NativeDBusConnection[F[_]] {

  /** Call a method on a remote DBus object.
    *
    * @param busName
    *   The bus name (e.g., "org.gnome.evolution.dataserver.Sources5")
    * @param objectPath
    *   The object path (e.g., "/org/gnome/evolution/dataserver/SourceManager")
    * @param iface
    *   The interface name (e.g., "org.gnome.evolution.dataserver.SourceManager")
    * @param method
    *   The method name to call
    * @param args
    *   Function to append arguments to the message (use DBusMessageBuilder)
    * @param timeoutMs
    *   Timeout in milliseconds (-1 for default)
    * @return
    *   The reply message for parsing
    */
  def callMethod(
      busName: String,
      objectPath: String,
      iface: String,
      method: String,
      args: DBusMessageBuilder => Unit,
      timeoutMs: Int = -1
  ): F[DBusReply]

  /** Call a method with no arguments */
  def callMethod(
      busName: String,
      objectPath: String,
      iface: String,
      method: String
  ): F[DBusReply] =
    callMethod(busName, objectPath, iface, method, _ => (), -1)

  /** Check if connection is still valid */
  def isConnected: F[Boolean]

  /** Close the connection */
  def close: F[Unit]
}

object NativeDBusConnection {

  /** Create a connection to the session bus */
  def sessionBus[F[_]: Async]: Resource[F, NativeDBusConnection[F]] =
    Resource.make(
      Async[F].blocking {
        Zone.acquire { z =>
          withError { error =>
            val conn = dbus_bus_get(DBUS_BUS_SESSION, error)
            checkError(error)
            if conn == null then
              throw DBusNativeError("Failed to connect to session bus")
            new LiveNativeDBusConnection[F](conn)
          }
        }
      }
    )(_.close)

  /** Create a connection to the system bus */
  def systemBus[F[_]: Async]: Resource[F, NativeDBusConnection[F]] =
    Resource.make(
      Async[F].blocking {
        Zone.acquire { z =>
          withError { error =>
            val conn = dbus_bus_get(DBUS_BUS_SYSTEM, error)
            checkError(error)
            if conn == null then
              throw DBusNativeError("Failed to connect to system bus")
            new LiveNativeDBusConnection[F](conn)
          }
        }
      }
    )(_.close)
}

/** Live implementation using libdbus */
private class LiveNativeDBusConnection[F[_]: Async](
    private[native] val conn: Ptr[DBusConnection]
) extends NativeDBusConnection[F] {

  override def isConnected: F[Boolean] =
    Async[F].blocking(dbus_connection_get_is_connected(conn) != 0)

  override def close: F[Unit] =
    Async[F].blocking {
      dbus_connection_close(conn)
      dbus_connection_unref(conn)
    }

  override def callMethod(
      busName: String,
      objectPath: String,
      iface: String,
      method: String,
      args: DBusMessageBuilder => Unit,
      timeoutMs: Int
  ): F[DBusReply] =
    Async[F].blocking {
      Zone.acquire { z =>
        // Create method call message
        val msg = dbus_message_new_method_call(
          toCString(busName)(using z),
          toCString(objectPath)(using z),
          toCString(iface)(using z),
          toCString(method)(using z)
        )

        if msg == null then
          throw DBusNativeError(s"Failed to create method call: $busName $objectPath $iface.$method")

        try
          // Append arguments
          if args != null then
            withIter { iter =>
              dbus_message_iter_init_append(msg, iter)
              args(new DBusMessageBuilder(iter, z))
            }

          // Send and block for reply
          withError { error =>
            val reply = dbus_connection_send_with_reply_and_block(
              conn,
              msg,
              if timeoutMs < 0 then 25000 else timeoutMs, // Default 25s timeout
              error
            )

            // Check for connection-level errors
            if dbus_error_is_set(error) != 0 then
              val name = fromCString(error._1)
              val msg = fromCString(error._2)
              throw DBusMethodError(name, msg)

            if reply == null then
              throw DBusNativeError("No reply received")

            try parseReply(reply)
            finally dbus_message_unref(reply)
          }
        finally
          dbus_message_unref(msg)
      }
    }

  private def parseReply(reply: Ptr[DBusMessage]): DBusReply = {
    val msgType = dbus_message_get_type(reply)

    if msgType == DBUS_MESSAGE_TYPE_ERROR then
      val errorName = fromCString(dbus_message_get_error_name(reply))
      val signature = fromCString(dbus_message_get_signature(reply))
      throw DBusMethodError(errorName, s"Error reply with signature: $signature")

    if msgType != DBUS_MESSAGE_TYPE_METHOD_RETURN then
      throw DBusNativeError(s"Unexpected message type: $msgType")

    // Parse the reply arguments
    new DBusReply(reply)
  }
}

/** Builder for constructing DBus method call arguments */
class DBusMessageBuilder private[native] (
    private val iter: Ptr[DBusMessageIter],
    private val zone: Zone
) {

  /** Append a string argument */
  def appendString(value: String): this.type = {
    val cstr = toCString(value)(using zone)
    if dbus_message_iter_append_basic(iter, DBUS_TYPE_STRING, cstr.asInstanceOf[Ptr[Byte]]) == 0 then
      throw DBusNativeError("Failed to append string argument")
    this
  }

  /** Append an object path argument */
  def appendObjectPath(value: String): this.type = {
    val cstr = toCString(value)(using zone)
    if dbus_message_iter_append_basic(iter, DBUS_TYPE_OBJECT_PATH, cstr.asInstanceOf[Ptr[Byte]]) == 0
    then
      throw DBusNativeError("Failed to append object path argument")
    this
  }

  /** Append an int32 argument */
  def appendInt32(value: Int): this.type = {
    val ptr = stackalloc[CInt]()
    !ptr = value
    if dbus_message_iter_append_basic(iter, DBUS_TYPE_INT32, ptr.asInstanceOf[Ptr[Byte]]) == 0 then
      throw DBusNativeError("Failed to append int32 argument")
    this
  }

  /** Append a uint32 argument */
  def appendUInt32(value: UInt): this.type = {
    val ptr = stackalloc[CUnsignedInt]()
    !ptr = value
    if dbus_message_iter_append_basic(iter, DBUS_TYPE_UINT32, ptr.asInstanceOf[Ptr[Byte]]) == 0
    then
      throw DBusNativeError("Failed to append uint32 argument")
    this
  }

  /** Append a boolean argument */
  def appendBoolean(value: Boolean): this.type = {
    val ptr = stackalloc[CInt]()
    !ptr = if value then 1 else 0
    if dbus_message_iter_append_basic(iter, DBUS_TYPE_BOOLEAN, ptr.asInstanceOf[Ptr[Byte]]) == 0
    then
      throw DBusNativeError("Failed to append boolean argument")
    this
  }

  /** Append a string array argument (as) */
  def appendStringArray(values: Seq[String]): this.type = {
    withIter { subIter =>
      if dbus_message_iter_open_container(iter, DBUS_TYPE_ARRAY, c"s", subIter) == 0 then
        throw DBusNativeError("Failed to open array container")

      values.foreach { value =>
        val cstr = toCString(value)(using zone)
        if dbus_message_iter_append_basic(subIter, DBUS_TYPE_STRING, cstr.asInstanceOf[Ptr[Byte]]) == 0
        then
          throw DBusNativeError("Failed to append array element")
      }

      if dbus_message_iter_close_container(iter, subIter) == 0 then
        throw DBusNativeError("Failed to close array container")
    }
    this
  }

  /** Open a container (array, struct, dict entry, variant) */
  def openContainer(containerType: Int, signature: String)(f: DBusMessageBuilder => Unit): this.type = {
    withIter { subIter =>
      val sig = toCString(signature)(using zone)
      if dbus_message_iter_open_container(iter, containerType, sig, subIter) == 0 then
        throw DBusNativeError("Failed to open container")

      try f(new DBusMessageBuilder(subIter, zone))
      catch
        case e: Exception =>
          // Abandon container on error
          dbus_message_iter_abandon_container(iter, subIter)
          throw e

      if dbus_message_iter_close_container(iter, subIter) == 0 then
        throw DBusNativeError("Failed to close container")
    }
    this
  }

  /** Open an array container with given element signature */
  def openArray(elementSignature: String)(f: DBusMessageBuilder => Unit): this.type =
    openContainer(DBUS_TYPE_ARRAY, elementSignature)(f)

  /** Open a variant container */
  def openVariant(signature: String)(f: DBusMessageBuilder => Unit): this.type =
    openContainer(DBUS_TYPE_VARIANT, signature)(f)

  /** Open a struct container */
  def openStruct(f: DBusMessageBuilder => Unit): this.type =
    openContainer(DBUS_TYPE_STRUCT, null)(f)
}

/** Parsed DBus method reply for reading arguments */
class DBusReply private[native] (private val msg: Ptr[DBusMessage]) {

  /** Get the signature of the reply */
  def signature: String =
    fromCString(dbus_message_get_signature(msg))

  /** Read arguments from the reply */
  def read[A](f: DBusReplyReader => A): A = {
    withIter { iter =>
      if dbus_message_iter_init(msg, iter) == 0 then
        throw DBusNativeError("No arguments in reply")
      f(new DBusReplyReader(iter))
    }
  }

  /** Read arguments, returning None if no arguments */
  def readOption[A](f: DBusReplyReader => A): Option[A] = {
    withIter { iter =>
      if dbus_message_iter_init(msg, iter) == 0 then
        None
      else
        Some(f(new DBusReplyReader(iter)))
    }
  }
}

/** Reader for extracting values from a DBus reply */
class DBusReplyReader private[native] (private val iter: Ptr[DBusMessageIter]) {

  /** Get the type of the current argument */
  def argType: Int =
    dbus_message_iter_get_arg_type(iter)

  /** Check if there are more arguments */
  def hasNext: Boolean =
    dbus_message_iter_has_next(iter) != 0

  /** Move to the next argument */
  def next(): Boolean =
    dbus_message_iter_next(iter) != 0

  /** Read a string value */
  def readString(): String = {
    if argType != DBUS_TYPE_STRING then
      throw DBusNativeError(s"Expected string, got type ${argType.toChar}")
    val ptr = stackalloc[CString]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    fromCString(!ptr)
  }

  /** Read an object path value */
  def readObjectPath(): String = {
    if argType != DBUS_TYPE_OBJECT_PATH then
      throw DBusNativeError(s"Expected object path, got type ${argType.toChar}")
    val ptr = stackalloc[CString]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    fromCString(!ptr)
  }

  /** Read an int32 value */
  def readInt32(): Int = {
    if argType != DBUS_TYPE_INT32 then
      throw DBusNativeError(s"Expected int32, got type ${argType.toChar}")
    val ptr = stackalloc[CInt]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    !ptr
  }

  /** Read a uint32 value */
  def readUInt32(): UInt = {
    if argType != DBUS_TYPE_UINT32 then
      throw DBusNativeError(s"Expected uint32, got type ${argType.toChar}")
    val ptr = stackalloc[CUnsignedInt]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    !ptr
  }

  /** Read a boolean value */
  def readBoolean(): Boolean = {
    if argType != DBUS_TYPE_BOOLEAN then
      throw DBusNativeError(s"Expected boolean, got type ${argType.toChar}")
    val ptr = stackalloc[CInt]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    !ptr != 0
  }

  /** Read a double value */
  def readDouble(): Double = {
    if argType != DBUS_TYPE_DOUBLE then
      throw DBusNativeError(s"Expected double, got type ${argType.toChar}")
    val ptr = stackalloc[CDouble]()
    dbus_message_iter_get_basic(iter, ptr.asInstanceOf[Ptr[Byte]])
    !ptr
  }

  /** Get element type for arrays */
  def elementType: Int =
    dbus_message_iter_get_element_type(iter)

  /** Get element count for arrays */
  def elementCount: Int =
    dbus_message_iter_get_element_count(iter)

  /** Read an array of strings */
  def readStringArray(): Seq[String] = {
    if argType != DBUS_TYPE_ARRAY then
      throw DBusNativeError(s"Expected array, got type ${argType.toChar}")

    val count = elementCount
    val builder = Seq.newBuilder[String]

    withIter { subIter =>
      dbus_message_iter_recurse(iter, subIter)
      var i = 0
      while i < count do
        val reader = DBusReplyReader(subIter)
        builder += reader.readString()
        dbus_message_iter_next(subIter)
        i += 1
    }

    builder.result()
  }

  /** Recurse into a container (array, struct, variant, dict entry) */
  def recurse(f: DBusReplyReader => Unit): Unit = {
    withIter { subIter =>
      dbus_message_iter_recurse(iter, subIter)
      f(new DBusReplyReader(subIter))
    }
  }

  /** Recurse into an array and process each element */
  def forEachElement(f: DBusReplyReader => Unit): Unit = {
    withIter { subIter =>
      dbus_message_iter_recurse(iter, subIter)
      while dbus_message_iter_get_arg_type(subIter) != DBUS_TYPE_INVALID do
        f(new DBusReplyReader(subIter))
        dbus_message_iter_next(subIter): Unit
    }
  }
}

/** Exception for DBus method errors */
case class DBusMethodError(name: String, message: String)
    extends RuntimeException(s"$name: $message")
