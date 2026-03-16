val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "com.risquanter",
    name := "fol-engine",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.apache.commons" % "commons-math3" % "3.6.1",
      "com.risquanter" % "simulation.util" % "0.8.0",
      // Pure-Scala HDR PRNG — will replace simulation.util.rng.HDR
      // once HDRSampler is switched to com.risquanter.hdr.HDR
      "com.risquanter" %% "hdr-rng" % "0.1.0-SNAPSHOT"
    )
  )
