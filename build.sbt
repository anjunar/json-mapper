import com.jsuereth.sbtpgp.PgpKeys._

ThisBuild / version := "1.2.0-SNAPSHOT"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature")
// Java sources compile for the oldest JDK the dependencies support, whatever JDK builds the release.
ThisBuild / javacOptions ++= Seq("--release", "17")
ThisBuild / homepage := Some(url("https://github.com/anjunar/json-mapper"))
ThisBuild / description :=
  "JSON mapping for Scala object graphs with in-place deserialization and structured domain binding."
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/json-mapper"),
    "scm:git:https://github.com/anjunar/json-mapper.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "anjunar",
    name = "Patrick Bittner",
    email = "anjunar@gmx.de",
    url = url("https://github.com/anjunar")
  )
)
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
Global / gpgCommand := {
  val windowsDefault = file("C:/Program Files/GnuPG/bin/gpg.exe")
  sys.env
    .get("GPG_COMMAND")
    .orElse(if (windowsDefault.exists()) Some(windowsDefault.getAbsolutePath) else None)
    .getOrElse("gpg")
}

lazy val jakartaApis = Seq(
  "jakarta.json.bind" % "jakarta.json.bind-api" % "3.0.1",
  "jakarta.persistence" % "jakarta.persistence-api" % "3.2.0",
  "jakarta.validation" % "jakarta.validation-api" % "3.1.1"
)

lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.2.20" % Test,
  "org.hibernate.validator" % "hibernate-validator" % "9.1.4.Final" % Test
)

/** Mapping, rules and JSON Schema export. Needs only the Jakarta APIs, no persistence provider. */
lazy val core = (project in file("core"))
  .settings(
    name := "json-mapper-core",
    moduleName := "json-mapper-core",
    libraryDependencies ++= Seq(
      "com.anjunar" %% "scala-reflect" % "1.1.5-SNAPSHOT",
      "com.anjunar" %% "scala-universe" % "1.0.5-SNAPSHOT",
      "tools.jackson.core" % "jackson-databind" % "3.1.1",
      "tools.jackson.module" %% "jackson-module-scala" % "3.1.1"
    ) ++ jakartaApis ++ testDependencies
  )

/** EntitySchema with JPA-backed properties. Keeps the published coordinates `json-mapper`. */
lazy val jpa = (project in file("jpa"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "json-mapper",
    moduleName := "json-mapper",
    libraryDependencies ++= Seq(
      "com.anjunar" %% "scala-reflect" % "1.1.5-SNAPSHOT",
      "org.hibernate.orm" % "hibernate-core" % "7.2.6.Final"
    ) ++ testDependencies
  )

lazy val root = (project in file("."))
  .aggregate(core, jpa)
  .settings(
    name := "json-mapper-root",
    publish / skip := true
  )
