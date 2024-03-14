import sbt.*
import sbt.Keys.{libraryDependencies, testFrameworks}

//noinspection TypeAnnotation
object Dependencies {

  val TestLib = Seq(
    testFrameworks += new TestFramework("munit.Framework"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"         % "0.7.29",
      "org.mockito"    % "mockito-core"  % "5.4.0",
      "org.gnieh"     %% "diffson-circe" % "4.4.0"
    ).map(_ % Test)
  )

  val Slack = Seq(
    libraryDependencies += "com.slack.api" % "slack-app-backend" % "1.38.3"
  )

  val Refined = Seq(
    libraryDependencies += "eu.timepit" %% "refined" % "0.11.0"
  )

  val NewTypes = Seq(
    libraryDependencies += "io.monix" %% "newtypes-core" % "0.2.3"
  )

  val Logging = Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j"  % "2.6.0",
      "ch.qos.logback" % "logback-classic" % "1.4.8" % Test,
      "ch.qos.logback" % "logback-core"    % "1.4.8" % Test,
      "org.slf4j"      % "jcl-over-slf4j"  % "2.0.7" % Test,
      "org.slf4j"      % "jul-to-slf4j"    % "2.0.7" % Test
    )
  )

  val Http4sVersion = "0.23.23"
  val Http4s = Seq(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl"          % Http4sVersion,
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-ember-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe"        % Http4sVersion
    )
  )

  val CirceVersion = "0.14.5"
  val Circe = Seq(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"   % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion
//      "io.circe" %% "circe-generic"        % CirceVersion,
//      "io.circe" %% "circe-optics"         % "0.14.1"
    )
  )

}
