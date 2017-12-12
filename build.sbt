name := "libpay-paybox-all"
version in ThisBuild := "1.0.0-SNAPSHOT"
organization in ThisBuild := "com.wix.pay"
licenses in ThisBuild := Seq("Apache License, ASL Version 2.0" → url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion in ThisBuild := "2.11.11"
scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-feature",
  "-Xlint",
  "-Xlint:-missing-interpolator"
)

libraryDependencies in ThisBuild ++= Seq(
  "org.specs2" %% "specs2-core" % "3.8.9" % "test",
  "org.specs2" %% "specs2-junit" % "3.8.9" % "test"
)

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

publishMavenStyle in ThisBuild := true
publishArtifact in Test := false
pomIncludeRepository in ThisBuild := (_ ⇒ false)
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "content/repositories/releases")
}

pomExtra in ThisBuild :=
  <url>https://github.com/wix/libpay-paybox</url>
    <scm>
      <url>git@github.com:wix/libpay-paybox.git</url>
      <connection>scm:git:git@github.com:wix/libpay-paybox.git</connection>
    </scm>
    <developers>
      <developer>
        <id>ohadraz</id>
        <name>Ohad Raz</name>
      </developer>
    </developers>

lazy val common = Project(
  id = "libpay-paybox-common"
  , base = file("libpay-paybox-common")
  , settings = Seq(name := "libpay-paybox-common") ++
      Seq(libraryDependencies ++= Seq("com.wix.pay" %% "libpay-api" % "1.6.0-SNAPSHOT", {
        if (scalaVersion.value startsWith "2.12")
          "org.json4s" %% "json4s-native" % "3.5.2"
        else
          "org.json4s" %% "json4s-native" % "3.3.0"
      } , "commons-codec" % "commons-codec" % "1.10" ))
)

lazy val testkit = Project(
  id = "libpay-paybox-testkit"
  , base = file("libpay-paybox-testkit")
  , settings = Seq(name := "libpay-paybox-testkit") ++
    Seq(libraryDependencies ++= Seq("com.wix.pay" %% "libpay-api" % "1.6.0-SNAPSHOT"
                                   ,"com.wix" %% "http-testkit" % "0.1.15"
                                   ,"com.google.http-client" % "google-http-client" % "1.21.0" ))
).dependsOn(common)

lazy val main = Project(
  id = "libpay-paybox"
  , base = file("libpay-paybox")
  , settings = Seq(name := "libpay-paybox") ++
    Seq(libraryDependencies += "com.google.http-client" % "google-http-client" % "1.21.0" )
).dependsOn(common, testkit)

lazy val noPublish = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val root = Project(
  id = "root"
  , base = file(".")
  , settings = noPublish
).aggregate(common, testkit, main)


