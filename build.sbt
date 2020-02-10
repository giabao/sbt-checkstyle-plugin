lazy val user = settingKey[String]("github user use in ScmInfo")

lazy val publishSettings = Seq(
  organization := "com.etsy",
  user := "etsy"
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-checkstyle",
    version := "3.1.2-SNAPSHOT",
    Compile / doc / sources := Nil,
    Test / publishArtifact := false,
    crossScalaVersions := Seq("2.12.10", "2.10.7"),
    scalaVersion := crossScalaVersions.value.head,
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.10" => "0.13.18"
        case "2.12" => "1.3.8"
      }
    },
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-Dplugin.version=" + version.value,
      "-Dplugin.org=" + organization.value
    ),
    scriptedBufferLog := false,
    libraryDependencies ++= Seq(
      "com.puppycrawl.tools"     % "checkstyle"   % "8.29",
      "net.sf.saxon"             % "Saxon-HE"     % "9.9.1-6",
      "org.scalatest"            %% "scalatest"   % "3.1.0" % Test,
      "junit"                    % "junit"        % "4.12" % Test,
      "org.scalatestplus"        %% "junit-4-12"  % "3.1.0.0" % Test,
      "com.github.stefanbirkner" % "system-rules" % "1.19.0" % Test
    ),
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
