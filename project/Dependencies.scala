import sbt.*
import sbt.Keys.{libraryDependencies, testFrameworks}

//noinspection TypeAnnotation
object Dependencies {

  val TestLib = Seq(
    testFrameworks += new TestFramework("munit.Framework"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"         % "1.0.2",
      "org.mockito"    % "mockito-core"  % "5.14.1",
      "org.gnieh"     %% "diffson-circe" % "4.6.0"
    ).map(_ % Test)
  )

  val Slack = Seq(
    libraryDependencies += "com.slack.api" % "slack-app-backend" % "1.44.0"
  )

  val Refined = Seq(
    libraryDependencies += "eu.timepit" %% "refined" % "0.11.2"
  )

  val NewTypes = Seq(
    libraryDependencies += "io.monix" %% "newtypes-core" % "0.3.0"
  )

  val Logging = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j"  % "2.7.0",
      "ch.qos.logback" % "logback-classic" % "1.5.9"  % Test,
      "ch.qos.logback" % "logback-core"    % "1.5.9"  % Test,
      "org.slf4j"      % "jcl-over-slf4j"  % "2.0.16" % Test,
      "org.slf4j"      % "jul-to-slf4j"    % "2.0.16" % Test
    )
  )

  val Http4s = Seq(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-ember-server",
      "org.http4s" %% "http4s-ember-client",
      "org.http4s" %% "http4s-circe"
    ).map(_ % "0.23.28")
  )

  val CirceVersion = "0.14.10"
  val Circe = Seq(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"   % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion
    )
  )

}
