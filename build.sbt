// See LICENSE for license details.

import chiselBuild.ChiselDependencies._
import chiselBuild.ChiselSettings

ChiselSettings.commonSettings

ChiselSettings.publishSettings

version := "1.2-SNAPSHOT"

val externalName = "Chisel.iotesters"

val internalName = "chisel_testers"

name := externalName

libraryDependencies ++= chiselLibraryDependencies(internalName)

libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "2.2.4",
                            "org.scalacheck" %% "scalacheck" % "1.12.4",
                            "com.github.scopt" %% "scopt" % "3.4.0")
    
pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
<licenses>
  <license>
    <name>BSD-style</name>
    <url>http://www.opensource.org/licenses/bsd-license.php</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<scm>
  <url>https://github.com/ucb-bar/chisel-testers.git</url>
  <connection>scm:git:github.com/ucb-bar/chisel-testers.git</connection>
</scm>
<developers>
  <developer>
    <id>jackbackrack</id>
    <name>Jonathan Bachrach</name>
    <url>http://www.eecs.berkeley.edu/~jrb/</url>
  </developer>
</developers>)

scalacOptions := Seq("-deprecation")

scalacOptions in (Compile, doc) <++= (baseDirectory, version) map { (bd, v) =>
  Seq("-diagrams", "-diagrams-max-classes", "25", "-sourcepath", bd.getAbsolutePath, "-doc-source-url", "https://github.com/ucb-bar/chisel-testers/tree/master/€{FILE_PATH}.scala")
}

dependsOn((chiselProjectDependencies(internalName)):_*)
