// SPDX-License-Identifier: Apache-2.0

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository
import mill.eval.Evaluator

import $file.CommonBuild

// An sbt layout with src in the top directory.
trait CrossUnRootedSbtModule extends CrossSbtModule {
  override def millSourcePath = super.millSourcePath / ammonite.ops.up
}

trait CommonModule extends CrossUnRootedSbtModule with PublishModule {
  def publishVersion = "2.5.1"

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "https://github.com/freechipsproject/chisel-testers.git",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("freechipsproject", "chisel-testers"),
    developers = Seq(
      Developer("jackbackrack",    "Jonathan Bachrach",      "https://eecs.berkeley.edu/~jrb/")
    )
  )

  override def repositories = super.repositories ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases")
  )

  override def scalacOptions = Seq(
    "-deprecation",
    "-explaintypes",
    "-feature", "-language:reflectiveCalls",
    "-unchecked",
    "-Xcheckinit",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator"
  ) ++ CommonBuild.scalacOptionsVersion(crossScalaVersion)

  override def javacOptions = CommonBuild.javacOptionsVersion(crossScalaVersion)
}

val crossVersions = Seq("2.12.14", "2.13.6")

// Make this available to external tools.
object chiselTesters extends Cross[ChiselTestersModule](crossVersions: _*) {
  def defaultVersion(ev: Evaluator) = T.command{
    println(crossVersions.head)
  }

  def compile = T{
    chiselTesters(crossVersions.head).compile()
  }

  def jar = T{
    chiselTesters(crossVersions.head).jar()
  }

  def test = T{
    chiselTesters(crossVersions.head).test.test()
  }

  def publishLocal = T{
    chiselTesters(crossVersions.head).publishLocal()
  }

  def docJar = T{
    chiselTesters(crossVersions.head).docJar()
  }
}

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the default development versions, not the "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.5.1",
  "firrtl" -> "1.5.1",
  "firrtl-interpreter" -> "1.5.1",
  "treadle" -> "1.5.1"
  )

def getVersion(dep: String, org: String = "edu.berkeley.cs") = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  ivy"$org::$dep:$version"
}

class ChiselTestersModule(val crossScalaVersion: String) extends CommonModule {
  override def artifactName = "chisel-iotesters"

  def chiselDeps = Agg("firrtl", "firrtl-interpreter", "treadle", "chisel3").map { d => getVersion(d) }

  override def ivyDeps = Agg(
    ivy"org.scalatest::scalatest:3.1.4",
    ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1",
    ivy"org.scalacheck::scalacheck:1.14.3",
    ivy"com.github.scopt::scopt:3.7.1"
 ) ++ chiselDeps

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

}
