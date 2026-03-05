/*
 * EDS4S - Evolution Data Server for Scala
 * Copyright (C) 2024 EDS4S Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package eds4s.dbus.native

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Low-level libdbus-1 FFI bindings for Scala Native.
  *
  * This provides direct bindings to the C libdbus library functions needed for
  * DBus communication. Higher-level wrappers are provided in DBusClient.scala.
  *
  * Usage: Link with libdbus-1 (automatically linked via @link annotation)
  */
@link("dbus-1")
@extern
object libdbus {

  // DBus type constants from dbus-protocol.h
  final val DBUS_TYPE_INVALID = 0 // '\0'
  final val DBUS_TYPE_BYTE = 121  // 'y'
  final val DBUS_TYPE_BOOLEAN = 98 // 'b'
  final val DBUS_TYPE_INT16 = 110 // 'n'
  final val DBUS_TYPE_UINT16 = 113 // 'q'
  final val DBUS_TYPE_INT32 = 105  // 'i'
  final val DBUS_TYPE_UINT32 = 117 // 'u'
  final val DBUS_TYPE_INT64 = 120  // 'x'
  final val DBUS_TYPE_UINT64 = 116 // 't'
  final val DBUS_TYPE_DOUBLE = 100 // 'd'
  final val DBUS_TYPE_STRING = 115  // 's'
  final val DBUS_TYPE_OBJECT_PATH = 111 // 'o'
  final val DBUS_TYPE_SIGNATURE = 103 // 'g'
  final val DBUS_TYPE_ARRAY = 97 // 'a'
  final val DBUS_TYPE_VARIANT = 118 // 'v'
  final val DBUS_TYPE_STRUCT = 114 // 'r' (but also 40 '(' and 41 ')')
  final val DBUS_TYPE_DICT_ENTRY = 101 // 'e' (but also 123 '{' and 125 '}')

  // Bus types
  final val DBUS_BUS_SESSION = 0
  final val DBUS_BUS_SYSTEM = 1
  final val DBUS_BUS_STARTER = 2

  // Message types
  final val DBUS_MESSAGE_TYPE_INVALID = 0
  final val DBUS_MESSAGE_TYPE_METHOD_CALL = 1
  final val DBUS_MESSAGE_TYPE_METHOD_RETURN = 2
  final val DBUS_MESSAGE_TYPE_ERROR = 3
  final val DBUS_MESSAGE_TYPE_SIGNAL = 4

  // === DBusError ===
  type DBusError = CStruct3[
    CString, // name
    CString, // message
    CInt // dummy1 (padding)
  ]

  @name("dbus_error_init")
  def dbus_error_init(error: Ptr[DBusError]): Unit = extern

  @name("dbus_error_free")
  def dbus_error_free(error: Ptr[DBusError]): Unit = extern

  @name("dbus_error_is_set")
  def dbus_error_is_set(error: Ptr[DBusError]): CInt = extern

  // === DBusConnection ===
  type DBusConnection = CStruct0 // opaque type

  @name("dbus_bus_get")
  def dbus_bus_get(`type`: CInt, error: Ptr[DBusError]): Ptr[DBusConnection] = extern

  @name("dbus_bus_get_private")
  def dbus_bus_get_private(`type`: CInt, error: Ptr[DBusError]): Ptr[DBusConnection] = extern

  @name("dbus_connection_ref")
  def dbus_connection_ref(connection: Ptr[DBusConnection]): Ptr[DBusConnection] = extern

  @name("dbus_connection_unref")
  def dbus_connection_unref(connection: Ptr[DBusConnection]): Unit = extern

  @name("dbus_connection_close")
  def dbus_connection_close(connection: Ptr[DBusConnection]): Unit = extern

  @name("dbus_connection_get_is_connected")
  def dbus_connection_get_is_connected(connection: Ptr[DBusConnection]): CInt = extern

  @name("dbus_connection_flush")
  def dbus_connection_flush(connection: Ptr[DBusConnection]): Unit = extern

  @name("dbus_connection_read_write_dispatch")
  def dbus_connection_read_write_dispatch(
      connection: Ptr[DBusConnection],
      timeout_milliseconds: CInt
  ): CInt = extern

  // === DBusMessage ===
  type DBusMessage = CStruct0 // opaque type

  @name("dbus_message_new_method_call")
  def dbus_message_new_method_call(
      destination: CString,
      path: CString,
      iface: CString,
      method: CString
  ): Ptr[DBusMessage] = extern

  @name("dbus_message_new_method_return")
  def dbus_message_new_method_return(method_call: Ptr[DBusMessage]): Ptr[DBusMessage] = extern

  @name("dbus_message_new_error")
  def dbus_message_new_error(
      reply_to: Ptr[DBusMessage],
      error_name: CString,
      error_message: CString
  ): Ptr[DBusMessage] = extern

  @name("dbus_message_ref")
  def dbus_message_ref(message: Ptr[DBusMessage]): Ptr[DBusMessage] = extern

  @name("dbus_message_unref")
  def dbus_message_unref(message: Ptr[DBusMessage]): Unit = extern

  @name("dbus_message_get_type")
  def dbus_message_get_type(message: Ptr[DBusMessage]): CInt = extern

  @name("dbus_message_get_sender")
  def dbus_message_get_sender(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_get_destination")
  def dbus_message_get_destination(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_get_path")
  def dbus_message_get_path(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_get_interface")
  def dbus_message_get_interface(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_get_member")
  def dbus_message_get_member(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_get_signature")
  def dbus_message_get_signature(message: Ptr[DBusMessage]): CString = extern

  @name("dbus_message_is_error")
  def dbus_message_is_error(message: Ptr[DBusMessage], error_name: CString): CInt = extern

  @name("dbus_message_get_error_name")
  def dbus_message_get_error_name(message: Ptr[DBusMessage]): CString = extern

  // === Sending Messages ===
  @name("dbus_connection_send")
  def dbus_connection_send(
      connection: Ptr[DBusConnection],
      message: Ptr[DBusMessage],
      client_serial: Ptr[CUnsignedInt]
  ): CInt = extern

  @name("dbus_connection_send_with_reply_and_block")
  def dbus_connection_send_with_reply_and_block(
      connection: Ptr[DBusConnection],
      message: Ptr[DBusMessage],
      timeout_milliseconds: CInt,
      error: Ptr[DBusError]
  ): Ptr[DBusMessage] = extern

  // === DBusMessageIter ===
  // DBusMessageIter is a struct that should be stack-allocated
  // Size varies by platform (pointer size), we use a fixed buffer
  // On 64-bit: 8*2 + 4*11 = ~60 bytes, on 32-bit: 4*13 = ~52 bytes
  // We allocate 64 bytes which is enough for both (8*8)
  type DBusMessageIter = CArray[CArray[Byte, Nat._8], Nat._8]

  @name("dbus_message_iter_init")
  def dbus_message_iter_init(message: Ptr[DBusMessage], iter: Ptr[DBusMessageIter]): CInt =
    extern

  @name("dbus_message_iter_init_append")
  def dbus_message_iter_init_append(message: Ptr[DBusMessage], iter: Ptr[DBusMessageIter]): Unit =
    extern

  @name("dbus_message_iter_has_next")
  def dbus_message_iter_has_next(iter: Ptr[DBusMessageIter]): CInt = extern

  @name("dbus_message_iter_next")
  def dbus_message_iter_next(iter: Ptr[DBusMessageIter]): CInt = extern

  @name("dbus_message_iter_get_arg_type")
  def dbus_message_iter_get_arg_type(iter: Ptr[DBusMessageIter]): CInt = extern

  @name("dbus_message_iter_get_element_type")
  def dbus_message_iter_get_element_type(iter: Ptr[DBusMessageIter]): CInt = extern

  @name("dbus_message_iter_get_signature")
  def dbus_message_iter_get_signature(iter: Ptr[DBusMessageIter]): CString = extern

  @name("dbus_message_iter_recurse")
  def dbus_message_iter_recurse(
      iter: Ptr[DBusMessageIter],
      sub: Ptr[DBusMessageIter]
  ): Unit = extern

  @name("dbus_message_iter_get_basic")
  def dbus_message_iter_get_basic(iter: Ptr[DBusMessageIter], value: Ptr[Byte]): Unit = extern

  @name("dbus_message_iter_append_basic")
  def dbus_message_iter_append_basic(
      iter: Ptr[DBusMessageIter],
      `type`: CInt,
      value: Ptr[Byte]
  ): CInt = extern

  @name("dbus_message_iter_open_container")
  def dbus_message_iter_open_container(
      iter: Ptr[DBusMessageIter],
      `type`: CInt,
      contained_signature: CString,
      sub: Ptr[DBusMessageIter]
  ): CInt = extern

  @name("dbus_message_iter_close_container")
  def dbus_message_iter_close_container(
      iter: Ptr[DBusMessageIter],
      sub: Ptr[DBusMessageIter]
  ): CInt = extern

  @name("dbus_message_iter_abandon_container")
  def dbus_message_iter_abandon_container(
      iter: Ptr[DBusMessageIter],
      sub: Ptr[DBusMessageIter]
  ): Unit = extern

  @name("dbus_message_iter_get_element_count")
  def dbus_message_iter_get_element_count(iter: Ptr[DBusMessageIter]): CInt = extern

  // === Memory ===
  @name("dbus_free")
  def dbus_free(memory: Ptr[Byte]): Unit = extern

  // === Utility ===
  @name("dbus_set_error_from_message")
  def dbus_set_error_from_message(error: Ptr[DBusError], message: Ptr[DBusMessage]): CInt = extern

  // === Request name ===
  final val DBUS_NAME_FLAG_ALLOW_REPLACEMENT = 1
  final val DBUS_NAME_FLAG_REPLACE_EXISTING = 2
  final val DBUS_NAME_FLAG_DO_NOT_QUEUE = 4

  final val DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER = 1
  final val DBUS_REQUEST_NAME_REPLY_IN_QUEUE = 2
  final val DBUS_REQUEST_NAME_REPLY_EXISTS = 3
  final val DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER = 4

  @name("dbus_bus_request_name")
  def dbus_bus_request_name(
      connection: Ptr[DBusConnection],
      name: CString,
      flags: CUnsignedInt,
      error: Ptr[DBusError]
  ): CInt = extern

  @name("dbus_bus_register")
  def dbus_bus_register(connection: Ptr[DBusConnection], error: Ptr[DBusError]): CInt = extern

  @name("dbus_bus_get_unique_name")
  def dbus_bus_get_unique_name(connection: Ptr[DBusConnection]): CString = extern
}

/** Helper utilities for working with libdbus */
object libdbusUtils:
  import libdbus.*

  /** Allocate a DBusError on stack and initialize it */
  inline def withError[A](inline f: Ptr[DBusError] => A): A = {
    val error = stackalloc[DBusError]()
    dbus_error_init(error)
    try f(error)
    finally dbus_error_free(error)
  }

  /** Allocate a DBusMessageIter on stack */
  inline def withIter[A](inline f: Ptr[DBusMessageIter] => A): A = {
    val iter = stackalloc[DBusMessageIter]()
    f(iter)
  }

  /** Check if error is set and throw exception */
  def checkError(error: Ptr[DBusError]): Unit = {
    if dbus_error_is_set(error) != 0 then
      val name = fromCString(error._1)
      val msg = fromCString(error._2)
      throw DBusNativeError(s"$name: $msg")
  }

/** Exception for DBus native errors */
case class DBusNativeError(message: String) extends RuntimeException(message)
