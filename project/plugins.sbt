addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")

// Local plugin for development - in a real project this would come from a published plugin
lazy val `sbt-schema-compat` = ProjectRef(file("..") / "compat-plugin", "compat-plugin")