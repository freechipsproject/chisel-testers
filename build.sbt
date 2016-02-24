name := "chisel-testers"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % "3.0",
                           "org.scalatest" % "scalatest_2.11" % "2.2.4",
                           "org.scalacheck" %% "scalacheck" % "1.12.4")

    
publishMavenStyle := true

publishArtifact in Test := false
pomIncludeRepository := { x => false }

pomExtra := <url>http://chisel.eecs.berkeley.edu/</url>
<licenses>
  <license>
    <name>BSD-style</name>
    <url>http://www.opensource.org/licenses/bsd-license.php</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<scm>
  <url>https://github.com/ucb-bar/chisel3.git</url>
  <connection>scm:git:github.com/ucb-bar/chisel3.git</connection>
</scm>
<developers>
  <developer>
    <id>jackbackrack</id>
    <name>Jonathan Bachrach</name>
    <url>http://www.eecs.berkeley.edu/~jrb/</url>
  </developer>
</developers>


publishTo <<= version { v: String =>
val nexus = "https://oss.sonatype.org/"
if (v.trim.endsWith("SNAPSHOT")) {
  Some("snapshots" at nexus + "content/repositories/snapshots")
}
else {
  Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
}


resolvers ++= Seq(
"Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
"Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
)
