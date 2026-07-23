ThisBuild / organization := "com.boombustgroup"
ThisBuild / scalaVersion := "3.8.2"
ThisBuild / scalacOptions ++= Seq(
  "-Werror",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-explain",
  "-Wunused:all",
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "amor-fati-economies",
    libraryDependencies ++= Seq(
      "org.apache.poi" % "poi-ooxml" % "5.4.1",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
  )
