import sbt.*
import sbt.Keys.{libraryDependencies, testFrameworks}

//noinspection TypeAnnotation
object Dependencies {

  val TestLib = Seq(
    testFrameworks += new TestFramework("munit.Framework"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"         % "1.0.2",
      "org.mockito"    % "mockito-core"  % "5.14.2",
      "org.gnieh"     %% "diffson-circe" % "4.6.0"
    ).map(_ % Test)
  )

  val Slack = libraryDependencies += "com.slack.api" % "slack-app-backend" % "1.44.2"

  val Refined = libraryDependencies += "eu.timepit" %% "refined" % "0.11.2"

  val NewTypes = libraryDependencies += "io.monix" %% "newtypes-core" % "0.3.0"

  val Logging = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j"  % "2.7.0",
      "ch.qos.logback" % "logback-classic" % "1.5.12" % Test,
      "ch.qos.logback" % "logback-core"    % "1.5.12" % Test,
      "org.slf4j"      % "jcl-over-slf4j"  % "2.0.16" % Test,
      "org.slf4j"      % "jul-to-slf4j"    % "2.0.16" % Test
    )
  )

  val Http4s = libraryDependencies ++= Seq(
    "http4s-dsl",
    "http4s-ember-server",
    "http4s-ember-client",
    "http4s-circe"
  ).map("org.http4s" %% _ % "0.23.29")

  val CirceVersion = "0.14.10"
  val Circe = libraryDependencies ++= Seq(
    "io.circe" %% "circe-core"   % CirceVersion,
    "io.circe" %% "circe-parser" % CirceVersion
  )

}
