lazy val scala213               = "2.13.13"
lazy val scala3                 = "3.3.0"
lazy val supportedScalaVersions = List(scala213, scala3)
ThisBuild / crossScalaVersions := supportedScalaVersions
ThisBuild / scalaVersion       := scala213

lazy val publishSettings = Seq(
  Test / publishArtifact := false,
  pomIncludeRepository   := (_ => false),
  organization           := "io.laserdisc",
  homepage               := Some(url("http://laserdisc.io/slack4s")),
  developers := List(
    Developer("barryoneill", "Barry O'Neill", "", url("https://github.com/barryoneill"))
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/laserdisc-io/slack4s/tree/master"),
      "scm:git:git@github.com:laserdisc-io/slack4s.git"
    )
  ),
  licenses := Seq(
    "MIT" -> url("https://raw.githubusercontent.com/laserdisc-io/slack4s/master/LICENSE")
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "slack4s",
    publishSettings,
    Seq(
      libraryDependencies ++= Seq(
        compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.2").cross(CrossVersion.full)),
        compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
      ).filterNot(_ => scalaVersion.value.startsWith("3.")),
      scalacOptions ++= Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-language:existentials,experimental.macros,higherKinds,implicitConversions,postfixOps",
        "-unchecked",
        "-Xfatal-warnings"
      ),
      scalacOptions ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, minor)) if minor >= 13 =>
            Seq(
              "-Xlint:-unused,_",
              "-Ywarn-numeric-widen",
              "-Ywarn-value-discard",
              "-Ywarn-unused:implicits",
              "-Ywarn-unused:imports",
              "-Xsource:3",
              "-Xlint:-byname-implicit",
              "-P:kind-projector:underscore-placeholders",
              "-Xlint",
              "-Ywarn-macros:after"
            )
          case _ => Seq.empty
        }
      },
      scalacOptions ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((3, _)) =>
            Seq(
              "-Ykind-projector:underscores",
              "-source:future",
              "-language:adhocExtensions",
              "-Wconf:msg=`= _` has been deprecated; use `= uninitialized` instead.:s"
            )
          case _ => Seq.empty
        }
      }
    ),
    Test / fork := true,
    // ------------------------- deps -------------------------
    excludeDependencies += "commons-logging",
    Dependencies.TestLib,
    Dependencies.Circe,
    Dependencies.Refined,
    Dependencies.NewTypes,
    Dependencies.Logging,
    Dependencies.Http4s,
    Dependencies.Slack,
    // ------------------ version fmt + buildinfo ------------------
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "slack4s",
    addCommandAlias("format", ";scalafmtAll;scalafmtSbt"),
    addCommandAlias("checkFormat", ";scalafmtCheckAll;scalafmtSbtCheck"),
    addCommandAlias("fullTest", ";clean;checkFormat;test")
  )
  .enablePlugins(BuildInfoPlugin, GitVersioning)
