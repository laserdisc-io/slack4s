import sbt.Keys.{libraryDependencies, testFrameworks}
import sbt._

//noinspection TypeAnnotation
object Dependencies {

  val TestLib = Seq(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"         % "0.7.29" % Test,
      "org.mockito"    % "mockito-core"  % "4.8.1"  % Test,
      "org.gnieh"     %% "diffson-circe" % "4.3.0"
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

  val Slack = Seq(
    libraryDependencies += "com.slack.api" % "slack-app-backend" % "1.26.1"
  )

  val Refined = Seq(
    libraryDependencies += "eu.timepit" %% "refined" % "0.10.1"
  )

  val Logging = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j"  % "2.5.0",
      "ch.qos.logback" % "logback-classic" % "1.4.5" % Test,
      "ch.qos.logback" % "logback-core"    % "1.4.5" % Test,
      "org.slf4j"      % "jcl-over-slf4j"  % "2.0.3" % Test,
      "org.slf4j"      % "jul-to-slf4j"    % "2.0.3" % Test
    )
  )

  val Http4sVersion = "0.23.12"
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
      "io.circe" %% "circe-optics"         % CirceVersion
    )
  )

}
