val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala-logic",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "com.risquanter" % "simulation.util" % "0.8.0"
    )
  )
