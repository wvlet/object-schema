import ReleaseTransformations._

scalaVersion := "2.12.1"

val buildSettings = Seq[Setting[_]](
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.12.1", "2.11.8"),
  organization := "org.wvlet",
  description := "A framework for structured data mapping",
  crossPaths := true,
  publishMavenStyle := true,
  // For performance testing, ensure each test run one-by-one
  concurrentRestrictions in Global := Seq(Tags.limit(Tags.Test, 1)),
  incOptions := incOptions.value.withNameHashing(true),
  logBuffered in Test := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  sonatypeProfileName := "org.wvlet",
  pomExtra := {
  <url>https://github.com/xerial/wvlet</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/wvlet/wvlet.git</connection>
      <developerConnection>scm:git:git@github.com:wvlet/wvlet.git</developerConnection>
      <url>github.com/wvlet/wvlet.git</url>
    </scm>
    <developers>
      <developer>
        <id>leo</id>
        <name>Taro L. Saito</name>
        <url>http://xerial.org/leo</url>
      </developer>
    </developers>
  },
  // Use sonatype resolvers
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  // Release settings
  releaseTagName := { (version in ThisBuild).value },
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
    pushChanges
  ),
  releaseCrossBuild := true
)

lazy val root = 
  Project(id = "object-schema-root", base = file(".")).settings(
    buildSettings,
    publishArtifact := false,
    publish := {},
    publishLocal := {}
).aggregate(objectSchema, wvletJmx, wvletOpts, wvletConfig)

val wvletLog = "org.wvlet" %% "wvlet-log" % "1.1"
val wvletTest = "org.wvlet" %% "wvlet-test" % "0.27" % "test"

lazy val objectSchema =
  Project(id = "object-schema", base = file("object-schema")).settings(
    buildSettings,
    description := "wvlet object schema inspector",
    libraryDependencies ++= Seq(
      wvletLog,
      wvletTest,
      "org.scala-lang" % "scalap" % scalaVersion.value,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val wvletJmx =
  Project(id = "wvlet-jmx", base = file("wvlet-jmx")).settings(
    buildSettings,
    description := "A library for exposing Scala object data through JMX",
    libraryDependencies ++= Seq(
      wvletLog,
      wvletTest,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  ).dependsOn(objectSchema)

lazy val wvletConfig =
  Project(id = "wvlet-config", base = file("wvlet-config")).settings(
    buildSettings,
    description := "wvlet configuration module",
    libraryDependencies ++= Seq(
      wvletTest,
      "org.yaml" % "snakeyaml" % "1.14"
    )
  ).dependsOn(objectSchema)

lazy val wvletOpts =
  Project(id = "wvlet-opts", base = file("wvlet-opts")).settings(
    buildSettings,
    description := "wvlet command-line option parser",
    libraryDependencies ++= Seq(
      wvletTest,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
    )
  ) dependsOn(objectSchema)
