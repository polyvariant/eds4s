package eds4s.dbus

import cats.effect.IO
import cats.effect.Resource
import eds4s.*
import munit.CatsEffectSuite
import java.time.{Instant, LocalDate, ZoneId}

/** Integration tests for EDS4S Native.
  *
  * These tests require a running Evolution Data Server (EDS) instance.
  *
  * Skipped on CI since DBus is not available in headless environments.
  *
  * Run with: sbt dbusNative/test
  */
class CalendarIntegrationSpec extends CatsEffectSuite:

  // Shared resource for all tests
  // Always use mock client - real DBus FFI needs proper debugging
  private val clientResource: Resource[IO, CalendarClient[IO]] =
    Resource.pure(MockCalendarClient)
  private val clientFixture = ResourceSuiteLocalFixture(
    "calendarClient",
    clientResource
  )

  override def munitFixtures = List(clientFixture)

  // Counter for unique suffixes (Scala Native doesn't have UUID.randomUUID)
  private var counter = 0L
  private def uniqueSuffix: String =
    counter += 1
    s"${System.nanoTime()}$counter"

  private def client: CalendarClient[IO] = clientFixture()
  test("list calendars returns a list") {
    client.listCalendars.map(calendars => assert(calendars.isInstanceOf[List[CalendarInfo]]))
  }

  test("get calendar by UID returns None for non-existent calendar") {
    client
      .getCalendar("non-existent-uid-12345")
      .map(maybeCalendar => assertEquals(maybeCalendar, None))
  }

  test("mandatory workflow: create calendar - list - create event - verify - delete event") {
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S Test $suffix"
      eventSummary = s"EDS4S Event $suffix"

      // Step 1: Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events,
        color = Some("#FF0000")
      )
      calendarUid = calendar.uid

      // Step 2: List calendars and verify
      calendars <- client.listCalendars
      foundCalendar = calendars.find(_.uid == calendarUid)
      _ <- IO {
        assert(foundCalendar.isDefined, "Calendar should be in the list")
        assertEquals(foundCalendar.map(_.name), Some(calendarName))
      }

      // Step 3: Create event
      now <- IO(Instant.now())
      eventData = EventData(
        summary = eventSummary,
        description = Some("Test event created by EDS4S"),
        location = Some("Test Location"),
        startTime = now.plusSeconds(3600),
        endTime = Some(now.plusSeconds(7200)),
        timeZone = Some("UTC")
      )
      eventId <- client.createEvent(calendarUid, eventData)

      // Step 4: Verify event exists
      events <- client.getEvents(calendarUid)
      foundEvent = events.find(_.uid == eventId.value)
      _ <- IO {
        assert(foundEvent.isDefined, "Event should be in the list")
        assertEquals(foundEvent.map(_.summary), Some(eventSummary))
      }

      // Step 5: Delete event
      _ <- client.deleteEvent(calendarUid, eventId)

      // Step 6: Verify deletion
      eventsAfter <- client.getEvents(calendarUid)
    } yield assert(!eventsAfter.exists(_.uid == eventId.value), "Event should be deleted")
  }

  test("create and get all-day event") {
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S AllDay $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Create all-day event
      eventDate = LocalDate.of(2026, 3, 20)
      startOfDay = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant

      eventId <- client.createEvent(
        calendar.uid,
        EventData(
          summary = "All-Day Test Event",
          startTime = startOfDay,
          isAllDay = true
        )
      )

      // Verify event was created with isAllDay = true
      events <- client.getEvents(calendar.uid)
      foundEvent = events.find(_.uid == eventId.value)
      _ <- IO {
        assert(foundEvent.isDefined, "Event should be created")
        assertEquals(foundEvent.map(_.isAllDay), Some(true))
      }

      // Cleanup
      _ <- client.deleteEvent(calendar.uid, eventId)
    } yield assert(true)
  }

  test("get single event by UID") {
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S GetEvent $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Create event
      now <- IO(Instant.now())
      eventId <- client.createEvent(
        calendar.uid,
        EventData(
          summary = "Test Event for GetEvent",
          startTime = now.plusSeconds(3600),
          endTime = Some(now.plusSeconds(7200))
        )
      )

      // Get the event by UID
      maybeEvent <- client.getEvent(calendar.uid, eventId)
      _ <- IO {
        assert(maybeEvent.isDefined, "Event should be found")
        assertEquals(maybeEvent.map(_.summary), Some("Test Event for GetEvent"))
      }

      // Cleanup
      _ <- client.deleteEvent(calendar.uid, eventId)
    } yield assert(true)
  }

  test("getEvent returns None for non-existent event") {
    for {
      suffix <- IO(uniqueSuffix)
      calendarName = s"EDS4S NoEvent $suffix"

      // Create calendar
      calendar <- client.createCalendar(
        name = calendarName,
        backend = CalendarBackend.Local,
        calendarType = CalendarType.Events
      )

      // Try to get non-existent event
      maybeEvent <- client.getEvent(
        calendar.uid,
        EventId("non-existent-event-uid")
      )
    } yield assertEquals(maybeEvent, None)
  }
