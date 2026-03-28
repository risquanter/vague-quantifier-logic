val scala3Version = "3.7.4"

ThisBuild / organization := "com.risquanter"
ThisBuild / version      := "0.6.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(folEngine.jvm, folEngine.js)
  .settings(
    publish / skip := true
  )

lazy val folEngine = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(
    name := "fol-engine",
    libraryDependencies ++= Seq(
      "org.scalameta"  %%% "munit"   % "1.0.0"          % Test,
      "com.risquanter" %%% "hdr-rng" % "0.1.0-SNAPSHOT"
    )
  )
