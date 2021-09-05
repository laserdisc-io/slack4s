import sbt.Keys.{ libraryDependencies, testFrameworks }
import sbt._

object Dependencies {

  val TestLib = Seq(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"       % "0.7.28" % Test,
      "org.mockito"   % "mockito-core" % "3.12.4" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

  val Slack = Seq(
    libraryDependencies += "com.slack.api" % "slack-app-backend" % "1.11.0"
  )

  val Refined = Seq(
    libraryDependencies += "eu.timepit" %% "refined" % "0.9.27"
  )

  val Logging = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "log4cats-slf4j" % "1.3.1",
      "ch.qos.logback" % "logback-classic" % "1.2.5" % Test,
      "ch.qos.logback" % "logback-core"    % "1.2.5" % Test,
      "org.slf4j"      % "jcl-over-slf4j"  % "1.7.32" % Test,
      "org.slf4j"      % "jul-to-slf4j"    % "1.7.32" % Test
    )
  )

  val Http4sVersion = "0.22.2"
  val Http4s = Seq(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl"          % Http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe"        % Http4sVersion
    )
  )

  val CirceVersion = "0.14.1"
  val Circe = Seq(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"           % CirceVersion,
      "io.circe" %% "circe-generic"        % CirceVersion,
      "io.circe" %% "circe-generic-extras" % CirceVersion,
      "io.circe" %% "circe-parser"         % CirceVersion
    )
  )

}
