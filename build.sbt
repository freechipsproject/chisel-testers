// See LICENSE for license details.

enablePlugins(BuildInfoPlugin)

ChiselProjectDependenciesPlugin.chiselBuildInfoSettings

ChiselProjectDependenciesPlugin.chiselProjectSettings

version := "1.2-SNAPSHOT"

// The Chisel projects we're dependendent on.
val chiselDeps = chisel.dependencies(Seq(
    ("edu.berkeley.cs" %% "firrtl" % "1.1-SNAPSHOT", "firrtl"),
    ("edu.berkeley.cs" %% "firrtl-interpreter" % "1.1-SNAPSHOT", "firrtl-interpreter"),
    ("edu.berkeley.cs" %% "chisel3" % "3.1-SNAPSHOT", "chisel3")
))

val dependentProjects = chiselDeps.projects

name := "chisel-iotesters"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalacheck" %% "scalacheck" % "1.13.4",
  "com.github.scopt" %% "scopt" % "3.5.0"
) ++ chiselDeps.libraries
    
  pomExtra := pomExtra.value ++
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
    </developers>

scalacOptions ++= Seq("-deprecation")

scalacOptions in (Compile, doc) ++= Seq(
  "-diagrams",
  "-diagrams-max-classes", "25",
  "-sourcepath", baseDirectory.value.getAbsolutePath,
  "-doc-source-url", "https://github.com/ucb-bar/chisel-testers/tree/master/€{FILE_PATH}.scala"
)

lazy val chisel_iotesters = (project in file("."))
  .dependsOn(dependentProjects.map(classpathDependency(_)):_*)
