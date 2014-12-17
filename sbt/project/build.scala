import sbt._
import Keys._

object BuildSettings {
  val buildOrganization = "edu.berkeley.cs"
  val buildVersion = "1.0"
  val buildScalaVersion = "2.10.1"

  def apply(sourcePath: String) = {
    Defaults.defaultSettings ++ Seq (
      organization := buildOrganization,
      version := buildVersion,
      scalaVersion := buildScalaVersion,
      scalaSource in Compile := Path.absolute(file(sourcePath)),

      libraryDependencies +=
        compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.1"),

      scalacOptions += "-P:continuations:enable"
    )
  }
}

object ChiselBuild extends Build {
   import BuildSettings._
   lazy val Core = Project("Core", file("Core"), settings = BuildSettings("../src/Core")) dependsOn(chisel)
   lazy val chisel = Project("chisel", file("chisel"), settings = BuildSettings("../../chisel/src/main/scala"))
}
