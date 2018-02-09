// See LICENSE for license details.

enablePlugins(BuildInfoPlugin)

// sbt-site - sbt-ghpages

enablePlugins(SiteScaladocPlugin)

enablePlugins(GhpagesPlugin)

git.remoteRepo := "git@github.com:freechipsproject/chisel-testers.git"

version := "1.2-SNAPSHOT"

name := "Chisel.iotesters"

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the default development versions, not the "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.1-SNAPSHOT",
  "firrtl" -> "1.1-SNAPSHOT",
  "firrtl-interpreter" -> "1.1-SNAPSHOT"
  )

def chiselVersion(proj: String): String = {
  sys.props.getOrElse(proj + "Version", defaultVersions(proj))
}

ChiselProjectDependenciesPlugin.chiselBuildInfoSettings

ChiselProjectDependenciesPlugin.chiselProjectSettings

// The Chisel projects we're dependendent on.
val chiselDeps = chisel.dependencies(Seq(
    ("edu.berkeley.cs" %% "firrtl" % chiselVersion("firrtl"), "firrtl"),
    ("edu.berkeley.cs" %% "firrtl-interpreter" % chiselVersion("firrtl-interpreter"), "firrtl-interpreter"),
    ("edu.berkeley.cs" %% "chisel3" % chiselVersion("chisel3"), "chisel3")
))

val dependentProjects = chiselDeps.projects

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.scalacheck" %% "scalacheck" % "1.13.4",
  "com.github.scopt" %% "scopt" % "3.6.0"
) ++ chiselDeps.libraries
    
  pomExtra := pomExtra.value ++
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
