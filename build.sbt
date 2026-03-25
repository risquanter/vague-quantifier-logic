val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "com.risquanter",
    name := "fol-engine",
    version := "0.2.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "com.risquanter" %% "hdr-rng" % "0.1.0-SNAPSHOT"
    )
  )
