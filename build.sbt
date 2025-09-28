ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "com.github.mohsen"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "schema-compat-core",
    scalaVersion := "3.3.3",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-schema" % "0.4.17",
      "dev.zio" %% "zio-schema-avro" % "0.4.17", 
      "dev.zio" %% "zio-schema-json" % "0.4.17",
      "dev.zio" %% "zio-json" % "0.6.2",
      "org.apache.avro" % "avro" % "1.11.3",
      "eu.timepit" %% "refined" % "0.11.0"
    )
  )

lazy val `compat-plugin` = project
  .in(file("compat-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-schema-compat",
    scalaVersion := "2.12.18",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "2.1.7",
      "org.apache.avro" % "avro" % "1.11.3",
      "dev.zio" %% "zio-json" % "0.6.2"
    ),
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )

lazy val demo = project
  .in(file("demo"))
  .dependsOn(core)
  .settings(
    name := "schema-compat-demo",
    scalaVersion := "3.3.3",
    commonSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.19",
      "eu.timepit" %% "refined" % "0.11.0"
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, `compat-plugin`, demo)
  .settings(
    name := "scala-compat-check-system",
    publish / skip := true
  )