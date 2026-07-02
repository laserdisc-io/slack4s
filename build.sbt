import laserdisc.sbt.CompileTarget.{Scala2And3, Scala2And3LTS}
import laserdisc.sbt.LaserDiscDevelopers.Barry

ThisBuild / laserdiscRepoName      := "slack4s"
ThisBuild / laserdiscCompileTarget := Scala2And3LTS

lazy val root = project
  .in(file("."))
  .settings(
    name       := "slack4s",
    developers := List(Barry),
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
    buildInfoPackage := "slack4s"
  )
  .enablePlugins(BuildInfoPlugin, LaserDiscDefaultsPlugin)
