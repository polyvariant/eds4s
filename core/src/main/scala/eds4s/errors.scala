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

package eds4s

/** Base trait for all EDS4S errors.
  *
  * All errors in EDS4S extend this trait, providing a consistent error handling
  * experience with cats-effect. Errors are modeled as exceptions to integrate
  * seamlessly with effect types like IO.
  *
  * Example usage:
  * {{{
  *   def getCalendar(uid: String): F[CalendarInfo] =
  *     // raises EdsError.CalendarNotFound if not found
  *     ...
  * }}}
  */
sealed trait EdsError extends RuntimeException {

  /** Human-readable error message describing what went wrong. */
  def message: String

  override def getMessage: String = message
}

object EdsError {

  /** Calendar with the specified UID was not found.
    *
    * This error occurs when attempting to access a calendar that does not exist
    * or has been deleted.
    *
    * @param uid
    *   The calendar UID that was not found
    */
  final case class CalendarNotFound(uid: String) extends EdsError {
    val message = s"Calendar not found: $uid"
  }

  /** Event was not found in the specified calendar.
    *
    * This error occurs when attempting to access or modify an event that does
    * not exist in the given calendar.
    *
    * @param uid
    *   The event UID that was not found
    * @param calendarUid
    *   The calendar where the event was expected (optional)
    */
  final case class EventNotFound(
      uid: String,
      calendarUid: Option[String] = None
  ) extends EdsError {
    val message = calendarUid match {
      case Some(calUid) => s"Event not found: $uid in calendar $calUid"
      case None         => s"Event not found: $uid"
    }
  }

  /** Failed to establish a connection to the DBus system bus.
    *
    * This error typically occurs when:
    *   - DBus daemon is not running
    *   - Application lacks permission to access DBus
    *   - System bus configuration is incorrect
    *
    * @param cause
    *   The underlying exception that caused the connection failure
    */
  final case class DBusConnectionFailed(cause: Throwable) extends EdsError {
    val message = s"Failed to connect to DBus: ${cause.getMessage}"
  }

  /** A DBus method call returned an error or failed unexpectedly.
    *
    * This error wraps failures from the underlying DBus communication layer
    * when calling methods on Evolution Data Server services.
    *
    * @param method
    *   The name of the DBus method that failed
    * @param detailMessage
    *   The error message returned by DBus
    */
  final case class DBusMethodFailed(method: String, detailMessage: String)
      extends EdsError {
    val message = s"DBus method '$method' failed: $detailMessage"
  }

  /** The required DBus service is not available on the system bus.
    *
    * This error occurs when Evolution Data Server is not installed or not
    * running. Ensure `evolution-data-server` is installed and the source
    * registry is active.
    *
    * @param serviceName
    *   The name of the DBus service that was not found
    */
  final case class DBusServiceNotAvailable(serviceName: String)
      extends EdsError {
    val message = s"DBus service not available: $serviceName"
  }

  /** The iCalendar (ICS) data could not be parsed or is malformed.
    *
    * This error occurs when EDS returns invalid ICS data, or when attempting to
    * create an event with malformed ICS content.
    *
    * @param ics
    *   The invalid ICS string or error description
    * @param cause
    *   The underlying parsing exception (optional)
    */
  final case class InvalidIcsFormat(
      ics: String,
      cause: Option[Throwable] = None
  ) extends EdsError {
    val message = cause match {
      case Some(e) => s"Invalid ICS format: ${e.getMessage}"
      case None    => "Invalid ICS format"
    }
  }

  /** Permission was denied for the requested operation.
    *
    * This error occurs when the user or application lacks the necessary
    * permissions to perform an operation on a calendar or event.
    *
    * @param operation
    *   The operation that was denied (e.g., "create", "delete")
    * @param resource
    *   The resource on which the operation was attempted (optional)
    */
  final case class PermissionDenied(
      operation: String,
      resource: Option[String] = None
  ) extends EdsError {
    val message = resource match {
      case Some(r) => s"Permission denied: cannot $operation on $r"
      case None    => s"Permission denied: cannot $operation"
    }
  }

  /** A calendar operation failed for a reason other than the specific error
    * types.
    *
    * This is a general-purpose error for calendar operations that fail due to
    * business logic or EDS-specific reasons.
    *
    * @param operation
    *   The name of the operation that failed (e.g., "createEvent")
    * @param reason
    *   A human-readable explanation of why the operation failed
    */
  final case class CalendarOperationFailed(operation: String, reason: String)
      extends EdsError {
    val message = s"Calendar operation '$operation' failed: $reason"
  }

  /** A calendar configuration value is invalid.
    *
    * This error occurs when attempting to create or modify a calendar with
    * invalid configuration parameters.
    *
    * @param field
    *   The configuration field that has an invalid value
    * @param value
    *   The invalid value that was provided
    * @param reason
    *   A description of why the value is invalid
    */
  final case class InvalidConfiguration(
      field: String,
      value: String,
      reason: String
  ) extends EdsError {
    val message =
      s"Invalid configuration for '$field' with value '$value': $reason"
  }

  /** Authentication failed for a calendar source.
    *
    * This error occurs when accessing a remote calendar (e.g., CalDAV) requires
    * authentication and the provided credentials are invalid or missing.
    *
    * @param source
    *   The calendar source that required authentication
    * @param reason
    *   A description of why authentication failed (optional)
    */
  final case class AuthenticationFailed(
      source: String,
      reason: Option[String] = None
  ) extends EdsError {
    val message = reason match {
      case Some(r) => s"Authentication failed for '$source': $r"
      case None    => s"Authentication failed for '$source'"
    }
  }

  /** A network error occurred while accessing a remote calendar.
    *
    * This error occurs for remote calendar sources (e.g., WebDAV, CalDAV) when
    * network connectivity issues prevent the operation from completing.
    *
    * @param url
    *   The URL that could not be accessed
    * @param cause
    *   The underlying network exception
    */
  final case class NetworkError(url: String, cause: Throwable)
      extends EdsError {
    val message = s"Network error accessing $url: ${cause.getMessage}"
  }

  /** The operation timed out before completing.
    *
    * This error occurs when an operation takes longer than the configured
    * timeout duration. This is common for network operations or when EDS is
    * unresponsive.
    *
    * @param operation
    *   A description of the operation that timed out
    * @param durationMs
    *   The timeout duration in milliseconds
    */
  final case class Timeout(operation: String, durationMs: Long)
      extends EdsError {
    val message = s"Timeout after ${durationMs}ms waiting for: $operation"
  }

  /** The requested operation is not supported.
    *
    * This error occurs when attempting to perform an operation that is not
    * supported by the current calendar backend or EDS version.
    *
    * @param operation
    *   The operation that is not supported
    * @param context
    *   Additional context about where the operation was attempted (optional)
    */
  final case class UnsupportedOperation(
      operation: String,
      context: Option[String] = None
  ) extends EdsError {
    val message = context match {
      case Some(ctx) => s"Unsupported operation '$operation' in context: $ctx"
      case None      => s"Unsupported operation: $operation"
    }
  }

  /** An unexpected error occurred that doesn't fit other error categories.
    *
    * This is a catch-all error type for unexpected exceptions that are not
    * covered by the more specific error types. When this error is raised, check
    * the cause for more information.
    *
    * @param cause
    *   The underlying exception that caused this error
    */
  final case class UnknownError(cause: Throwable) extends EdsError {
    val message = s"Unknown error: ${cause.getMessage}"
  }
}

/** Type alias for error-aware computations using Either.
  *
  * This type alias can be used for functions that return results in an Either
  * context with EdsError on the left side.
  *
  * Example:
  * {{{
  *   def safeGetCalendar(uid: String): EdsResult[CalendarInfo] =
  *     // Returns Right(CalendarInfo) on success, Left(EdsError) on failure
  *     ...
  * }}}
  */
type EdsResult[A] = Either[EdsError, A]
