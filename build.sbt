// EDS4S - Evolution Data Server for Scala
// Copyright (C) 2024 EDS4S Contributors
// SPDX-License-Identifier: Apache-2.0

import org.typelevel.sbt.gha.RefPredicate
import org.typelevel.sbt.gha.Ref

// sbt-typelevel global settings
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / organization := "org.polyvariant"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2026)
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
val catsEffectVersion = "3.6.3"
val fs2Version = "3.12.2"
val dbusJavaVersion = "5.2.0"
val ical4jVersion = "4.2.3"
val weaverVersion = "0.11.3"

lazy val root = project
  .in(file("."))
  .aggregate(core, dbus, examples)
  .enablePlugins(NoPublishPlugin)
  .settings(
    name := "eds4s"
  )

lazy val core = project
  .in(file("core"))
  .settings(
    name := "eds4s-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.mnode.ical4j" % "ical4j" % ical4jVersion,
      "org.typelevel" %% "weaver-cats" % weaverVersion % Test
    )
  )

lazy val dbus = project
  .in(file("dbus"))
  .dependsOn(core)
  .settings(
    name := "eds4s-dbus",
    libraryDependencies ++= Seq(
      "com.github.hypfvieh" % "dbus-java-core" % dbusJavaVersion,
      "com.github.hypfvieh" % "dbus-java-transport-native-unixsocket" % dbusJavaVersion,
      "org.typelevel" %% "weaver-cats" % weaverVersion % Test
    )
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(dbus)
  .settings(
    name := "eds4s-examples",
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
    Compile / run / fork := true,
    javaOptions ++= Seq(
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED"
    )
  )
