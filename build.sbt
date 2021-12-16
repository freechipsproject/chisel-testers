// SPDX-License-Identifier: Apache-2.0

// sbt-site - sbt-ghpages

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

git.remoteRepo := "git@github.com:freechipsproject/chisel-testers.git"

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

organization := "edu.berkeley.cs"
version := "2.5-SNAPSHOT"
name := "chisel-iotesters"

scalaVersion := "2.12.14"

crossScalaVersions := Seq("2.12.14", "2.13.6")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the default development versions, not the "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.5-SNAPSHOT",
  "firrtl" -> "1.5-SNAPSHOT",
  "firrtl-interpreter" -> "1.5-SNAPSHOT",
  "treadle" -> "1.5-SNAPSHOT"
  )

libraryDependencies ++= Seq("chisel3","firrtl","firrtl-interpreter", "treadle").map { dep: String =>
    "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % defaultVersions("chisel3") cross CrossVersion.full)

// sbt 1.2.6 fails with `Symbol 'term org.junit' is missing from the classpath`
// when compiling tests under 2.11.12
// An explicit dependency on junit seems to alleviate this.
libraryDependencies ++= Seq(
  "junit" % "junit" % "4.13",
  "org.scalatest" %% "scalatest" % "3.2.2",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.1.1.1",
  "org.scalacheck" %% "scalacheck" % "1.14.3",
  "com.github.scopt" %% "scopt" % "3.7.1"
)

publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

// Don't add 'scm' elements if we have a git.remoteRepo definition.
pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
<licenses>
  <license>
    <name>apache_v2</name>
    <url>https://opensource.org/licenses/Apache-2.0</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<developers>
  <developer>
    <id>jackbackrack</id>
    <name>Jonathan Bachrach</name>
    <url>http://www.eecs.berkeley.edu/~jrb/</url>
  </developer>
</developers>)


publishTo := {
  val v = version.value
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  }
  else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}


resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",
  "-diagrams-max-classes", "25",
  "-sourcepath", baseDirectory.value.getAbsolutePath,
  "-doc-source-url", "https://github.com/ucb-bar/chisel-testers/tree/master/€{FILE_PATH}.scala"
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
