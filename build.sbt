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
      "scm:git:git@github.com:laserdisc-io/slack4s.git",
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
    Seq(
      scalaVersion := "2.13.6",
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      scalacOptions ++= Seq(
        "-encoding",
        "UTF-8",
        "-deprecation",
        "-unchecked",
        "-feature",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-Xlint:_,-byname-implicit" // see https://github.com/scala/bug/issues/12072
//        "-Xfatal-warnings"
      )
    ),
    Test / fork := true,
    // ------------------------- deps -------------------------
    excludeDependencies += "commons-logging",
    Dependencies.TestLib,
    Dependencies.Circe,
    Dependencies.Refined,
    Dependencies.Logging,
    Dependencies.Http4s,
    Dependencies.Slack,
    // ------------------ version fmt + buildinfo ------------------
    buildInfoKeys           := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage        := "slack4s",
    addCommandAlias("format", ";scalafmtAll;scalafmtSbt"),
    addCommandAlias("checkFormat", ";scalafmtCheckAll;scalafmtSbtCheck"),
    addCommandAlias("fullTest", ";clean;checkFormat;coverage;test;coverageReport")
  )
  .enablePlugins(BuildInfoPlugin, GitVersioning)
