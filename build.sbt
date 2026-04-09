import com.jsuereth.sbtpgp.PgpKeys._

ThisBuild / version := "1.0.2"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.8.3"
ThisBuild / homepage := Some(url("https://github.com/anjunar/json-mapper"))
ThisBuild / description := "JSON mapping for Scala object graphs with in-place deserialization and structured domain binding."
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

lazy val root = (project in file("."))
  .settings(
    name := "json-mapper",
    moduleName := "json-mapper",
    libraryDependencies ++= Seq(
      "com.anjunar" %% "scala-universe" % "1.0.0",
      "com.google.guava" % "guava" % "33.5.0-jre",
      "jakarta.json.bind" % "jakarta.json.bind-api" % "3.0.1",
      "jakarta.persistence" % "jakarta.persistence-api" % "3.2.0",
      "jakarta.validation" % "jakarta.validation-api" % "3.1.1",
      "tools.jackson.core" % "jackson-databind" % "3.1.1",
      "tools.jackson.module" %% "jackson-module-scala" % "3.1.1",
      "org.hibernate.orm" % "hibernate-core" % "7.2.6.Final",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    )
  )
