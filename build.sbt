lazy val user = settingKey[String]("github user use in ScmInfo")

lazy val publishSettings = Seq(
  organization := "com.sandinh", // "com.etsy"
  user := "giabao" //etsy
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-checkstyle",
    version := "3.2.0-SNAPSHOT",
    Compile / doc / sources := Nil,
    Test / publishArtifact := false,
    scalaVersion := "2.12.10",
    pluginCrossBuild / sbtVersion := "1.3.8",
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-Dplugin.version=" + version.value,
      "-Dplugin.org=" + organization.value
    ),
    scriptedBufferLog := false,
    libraryDependencies ++= Seq(
      "com.puppycrawl.tools"     % "checkstyle"   % "8.29",
      "net.sf.saxon"             % "Saxon-HE"     % "9.9.1-6"
    ),
    transitiveClassifiers := Seq("sources"),
    scalastyleConfig := file("scalastyle.xml"),
    scalastyleFailOnError := true
  )
  .settings(infoSettings: _*)

lazy val infoSettings = publishSettings ++ Seq(
  publishMavenStyle := false,
  bintrayRepository := "sbt-plugins",
  bintrayOrganization in bintray := None,
  bintrayPackageLabels := Seq(
    "sbt",
    "sbt-checkstyle",
    "checkstyle"
  ),
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  developers := List(
    Developer(
      "ajsquared",
      "Andrew Johnson",
      "andrew@andrewjamesjohnson.com",
      url("https://github.com/ajsquared")
    ),
    Developer("ohze", "Bùi Việt Thành", "thanhbv@sandinh.net", url("https://sandinh.com"))
  ),
  scmInfo := {
    val u = user.value
    Some(
      ScmInfo(
        url(s"https://github.com/$u/sbt-checkstyle-plugin"),
        s"scm:git:git://github.com:$u/sbt-checkstyle-plugin"
      )
    )
  }
)
