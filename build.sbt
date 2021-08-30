lazy val root = project
  .in(file("."))
  .settings(
    name         := "slack4s",
    organization := "io.laserdisc",
    Seq(
      scalaVersion := "2.13.6",
      homepage     := Some(url("https://github.com/laserdisc-io/slack4s")),
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
        "-Xlint:_,-byname-implicit", // see https://github.com/scala/bug/issues/12072
        "-Xfatal-warnings"
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
    git.formattedShaVersion := git.gitHeadCommit.value.map(_.take(8)),
    buildInfoKeys           := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage        := "slack4s",
    addCommandAlias("format", ";scalafmtAll;scalafmtSbt"),
    addCommandAlias("checkFormat", ";scalafmtCheckAll;scalafmtSbtCheck"),
    addCommandAlias("build", ";checkFormat;clean;test")
  )
  .enablePlugins(BuildInfoPlugin, GitVersioning)
