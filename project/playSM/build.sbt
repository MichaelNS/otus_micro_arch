name := "playSM"

//common settings for the project and subprojects
lazy val commonSettings = Seq(
  organization := "ru.ns",
  version := "0.1.3",
  scalaVersion := "2.13.8",

  //  https://tpolecat.github.io/2017/04/25/scalac-flags.html
  // https://nathankleyn.com/2019/05/13/recommended-scalac-flags-for-2-13/

  // https://docs.scala-lang.org/overviews/compiler-options/index.html
  // https://gist.github.com/tabdulradi/aa7450921756cd22db6d278100b2dac8
  // https://github.com/scala/scala/blob/2.12.x/src/compiler/scala/tools/nsc/settings/StandardScalaSettings.scala
  // https://github.com/scala/scala/blob/2.12.x/src/compiler/scala/tools/nsc/settings/ScalaSettings.scala

  scalacOptions ++= Seq(
    "-target:8",

    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    //  "-language:experimental.macros",   // Allow macro definition (besides implementation and application). Disabled, as this will significantly change in Scala 3
    "-language:higherKinds", // Allow higher-kinded types
    //  "-language:implicitConversions",   // Allow definition of implicit functions called views. Disabled, as it might be dropped in Scala 3. Instead use extension methods (implemented as implicit class Wrapper(val inner: Foo) extends AnyVal {}
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    //    "-Xfatal-warnings", // Fail the compilation if there are any warnings.

    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    //    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:unused", // TODO check if we still need -Wunused below
    "-Xlint:nonlocal-return", // A return statement used an exception for flow control.
    "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
    "-Xlint:serial", // @SerialVersionUID on traits and non-serializable classes.
    "-Xlint:valpattern", // Enable pattern checks in val definitions.
    "-Xlint:eta-zero", // Warn on eta-expansion (rather than auto-application) of zero-ary method.
    "-Xlint:eta-sam", // Warn on eta-expansion to meet a Java-defined functional interface that is not explicitly annotated with @FunctionalInterface.
    "-Xlint:deprecation", // Enable linted deprecations.
    "-Xlint:implicit-recursion",

    "-Wdead-code", // Warn when dead code is identified.
    "-Wextra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Wmacros:both", // Lints code before and after applying a macro
    "-Wnumeric-widen", // Warn when numerics are widened.
    "-Woctal-literal", // Warn on obsolete octal syntax.
    //    "-Wself-implicit", // Warn when an implicit resolves to an enclosing self-definition.
    "-Wunused:imports", // Warn if an import selector is not referenced.
    "-Wunused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Wunused:privates", // Warn if a private member is unused.
    "-Wunused:locals", // Warn if a local definition is unused.
    "-Wunused:explicits", // Warn if an explicit parameter is unused.
    "-Wunused:implicits", // Warn if an implicit parameter is unused.
    "-Wunused:params", // Enable -Wunused:explicits,implicits.
    "-Wunused:linted",
    "-Wvalue-discard", // Warn when non-Unit expression results are unused.

    "-Ybackend-parallelism", "8", // Enable paralellisation — change to desired number!
    "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
    "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.
  )
)

// This is common, non-Play code
lazy val fileutils = project in file("modules/fileutils")

val playSlick = "5.0.0"
val slick = "3.3.3"
val tmingleiDep = "0.20.2"

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(routesGenerator := InjectedRoutesGenerator)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slick,
      "com.typesafe.slick" %% "slick-codegen" % slick,

      "com.github.tminglei" %% "slick-pg" % tmingleiDep,

      "com.typesafe.play" %% "play-slick" % playSlick,
      "com.typesafe.play" %% "play-slick-evolutions" % playSlick,

      "org.webjars" %% "webjars-play" % "2.8.8",
      "org.webjars" % "foundation" % "6.4.3",

      guice,
      ws,

      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,

      "com.lihaoyi" %% "sourcecode" % "0.2.7",
      "com.lihaoyi" %% "pprint" % "0.7.1",
      "org.apache.commons" % "commons-text" % "1.9", // https://stackoverflow.com/questions/11145681/how-to-convert-a-string-with-unicode-encoding-to-a-string-of-letters

      //    scala-fixture:
      "com.github.tototoshi" % "scala-fixture_2.12" % "0.4.1" % Test,
      "com.h2database" % "h2" % "2.0.206" % Test,
      "org.flywaydb" % "flyway-core" % "8.4.1" % Test,

      "org.scalamock" %% "scalamock" % "5.2.0" % Test,
      "org.scalatest" %% "scalatest" % "3.2.10" % Test
    )

  )
  .enablePlugins(PlayScala)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(fileutils)
  .dependsOn(fileutils)

//to generate models/db/Tables.scala
addCommandAlias("tables", "runMain utils.db.SourceCodeGenerator")

// sbt-scoverage:
coverageMinimum := 29.25
coverageFailOnMinimum := true
coverageHighlighting := true

ThisBuild / turbo := true