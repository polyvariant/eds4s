// EDS4S - Evolution Data Server for Scala
// Copyright (C) 2024 EDS4S Contributors
// SPDX-License-Identifier: Apache-2.0

import org.typelevel.sbt.gha.{RefPredicate, Ref, WorkflowStep, MatrixExclude}
import org.typelevel.sbt.gha.Ref
import sbtcrossproject.CrossPlugin.autoImport._
import scalanativecrossproject.ScalaNativeCrossPlugin.autoImport._

// sbt-typelevel global settings
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / organization := "org.polyvariant"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("majk-p", "Michał Pawlik"))

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / tlJdkRelease := Some(17)
ThisBuild / tlFatalWarnings := false

// GitHub workflow publish on main and tags
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v"))
)

// Dependencies
// Note: Using versions that support Scala Native 0.5
val catsEffectVersion = "3.7.0-RC1"
val fs2Version = "3.13.0-M8"
val dbusJavaVersion = "5.2.0"
val ical4jVersion = "4.2.3"
val weaverVersion = "0.11.3" // JVM only
val munitCatsEffectVersion = "2.2.0-RC1" // Cross-platform (JVM + Native)

// Scala Native version
val scalaNativeVersion = "0.5.10"

// Scala Native CI configuration
// Add platform matrix for JVM and Native
ThisBuild / githubWorkflowBuildMatrixAdditions += "platform" -> List("JVM", "Native")

// Exclude non-first Java versions for Native (only need one Java version for Native)
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  val javaVersions = (ThisBuild / githubWorkflowJavaVersions).value
  javaVersions.tail.map { java =>
    MatrixExclude(Map("platform" -> "Native", "java" -> java.render))
  }
}

// Setup Scala Native toolchain (clang, LLVM) for Native platform
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Run(
    List("sudo apt-get update", "sudo apt-get install -y clang lldb lld libunwind-dev libgc-dev libdbus-1-dev"),
    name = Some("Install Scala Native dependencies"),
    cond = Some("matrix.platform == 'Native'")
  )
)

// Custom build steps for cross-platform testing
ThisBuild / githubWorkflowBuild := Seq(
  // Native linking step (required before running native tests)
  WorkflowStep.Sbt(
    List("coreNative / Test / nativeLink"),
    name = Some("Link Native binary"),
    cond = Some("matrix.platform == 'Native'")
  ),
  // Test coreJVM and dbus/exampes on JVM platform
  WorkflowStep.Sbt(
    List("coreJVM/test", "dbusJVM/test", "examples/compile"),
    name = Some("Test JVM modules"),
    cond = Some("matrix.platform == 'JVM'")
  ),
  // Test coreNative on Native platform
  WorkflowStep.Sbt(
    List("coreNative/test", "dbusNative/test"),
    name = Some("Test Native modules"),
    cond = Some("matrix.platform == 'Native'")
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(core.jvm, core.native, dbus.jvm, dbus.native, examples)
  .enablePlugins(NoPublishPlugin)
  .settings(
    name := "eds4s"
  )

// Core module - cross-compiled for JVM and Native
// Shared code: algebra, models, errors
// JVM-specific: ical4j-based IcalConverter
// Native-specific: pure Scala IcalConverter
lazy val core = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(
    name := "eds4s-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .jvmSettings(
    // JVM-specific: use ical4j for ICS parsing
    libraryDependencies += "org.mnode.ical4j" % "ical4j" % ical4jVersion
  )
  .nativeSettings(
    // Native-specific: no ical4j, pure Scala implementation
    // The IcalConverter for Native is implemented in native/src/main/scala
    tlVersionIntroduced := Map("3" -> "0.1.0")
  )

// DBus module - cross-compiled for JVM and Native
// JVM: uses dbus-java library
// Native: uses libdbus-1 FFI bindings
lazy val dbus = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("dbus"))
  .dependsOn(core)
  .settings(
    name := "eds4s-dbus"
  )
  .jvmSettings(
    // JVM-specific: use dbus-java library with weaver for testing
    libraryDependencies ++= Seq(
      "com.github.hypfvieh" % "dbus-java-core" % dbusJavaVersion,
      "com.github.hypfvieh" % "dbus-java-transport-native-unixsocket" % dbusJavaVersion,
      "org.typelevel" %% "weaver-cats" % weaverVersion % Test
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )
  .nativeSettings(
    // Native-specific: use libdbus-1 FFI bindings with munit for testing
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    nativeConfig ~= { c =>
      c.withLinkingOptions("-ldbus-1" :: Nil)
    }
    , tlVersionIntroduced := Map("3" -> "0.1.0")
  )
// Examples - JVM only (depends on dbus.jvm for now)
// TODO: Add native examples once dbus.native is ready
lazy val examples = project
  .in(file("examples"))
  .dependsOn(dbus.jvm)
  .settings(
    name := "eds4s-examples",
    tlVersionIntroduced := Map("3" -> "0.1.0"),
    publish / skip := true,
    Compile / run / fork := true,
    javaOptions ++= Seq(
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED"
    )
  )
