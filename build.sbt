// See LICENSE for license details.

// sbt-site - sbt-ghpages

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

git.remoteRepo := "git@github.com:freechipsproject/chisel-testers.git"

def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

organization := "edu.berkeley.cs"
version := "1.3-SNAPSHOT"
name := "Chisel.iotesters"

scalaVersion := "2.12.6"

crossScalaVersions := Seq("2.12.6", "2.11.12")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the default development versions, not the "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.2-SNAPSHOT",
  "firrtl" -> "1.2-SNAPSHOT",
  "firrtl-interpreter" -> "1.2-SNAPSHOT",
  "treadle" -> "1.1-SNAPSHOT"
  )

libraryDependencies ++= Seq("chisel3","firrtl","firrtl-interpreter", "treadle").map { dep: String =>
    "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
}

// sbt 1.2.6 fails with `Symbol 'term org.junit' is missing from the classpath`
// when compiling tests under 2.11.12
// An explicit dependency on junit seems to alleviate this.
libraryDependencies ++= Seq(
  "junit" % "junit" % "4.12",
  "org.scalatest" %% "scalatest" % "3.0.8",
  "org.scalacheck" %% "scalacheck" % "1.14.0",
  "com.github.scopt" %% "scopt" % "3.7.1"
)
    
publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

// Don't add 'scm' elements if we have a git.remoteRepo definition.
pomExtra := (<url>http://chisel.eecs.berkeley.edu/</url>
<licenses>
  <license>
    <name>BSD-style</name>
    <url>http://www.opensource.org/licenses/bsd-license.php</url>
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

scalacOptions := Seq("-deprecation") ++ scalacOptionsVersion(scalaVersion.value)

scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",
  "-diagrams-max-classes", "25",
  "-sourcepath", baseDirectory.value.getAbsolutePath,
  "-doc-source-url", "https://github.com/ucb-bar/chisel-testers/tree/master/€{FILE_PATH}.scala"
)

javacOptions ++= javacOptionsVersion(scalaVersion.value)
