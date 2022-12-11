import play.core.PlayVersion.akkaVersion

val playSlick = "5.0.0"
val slick = "3.3.3"
val tmingleiDep = "0.20.2"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """auth-app""",
    organization := "ru.ns",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.13.10",
    libraryDependencies ++= Seq(
      guice,
      "com.google.inject" % "guice" % "5.1.0",
      "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0",

      ws,
      "org.abstractj.kalium" % "kalium" % "0.8.0",
      "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,

      "com.typesafe.slick" %% "slick" % slick,
      "com.typesafe.slick" %% "slick-codegen" % slick,

      "com.github.tminglei" %% "slick-pg" % tmingleiDep,

      "com.typesafe.play" %% "play-slick" % playSlick,
      "com.typesafe.play" %% "play-slick-evolutions" % playSlick,

      "com.lihaoyi" %% "sourcecode" % "0.3.0",
      "com.lihaoyi" %% "pprint" % "0.8.1",
      "org.apache.commons" % "commons-text" % "1.10.0", // https://stackoverflow.com/questions/11145681/how-to-convert-a-string-with-unicode-encoding-to-a-string-of-letters
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      //      "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    )
  )
