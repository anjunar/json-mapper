ThisBuild / version := "1.0.0"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.8.3"

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