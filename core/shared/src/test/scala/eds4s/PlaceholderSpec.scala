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

package eds4s

import cats.effect.IO
import munit.CatsEffectSuite

class PlaceholderSpec extends CatsEffectSuite {
  test("placeholder test - models are accessible") {
    IO {
      val event = Event(
        uid = "test-uid",
        summary = "Test Event",
        startTime = java.time.Instant.now()
      )
      assertEquals(event.summary, "Test Event")
    }
  }
}
