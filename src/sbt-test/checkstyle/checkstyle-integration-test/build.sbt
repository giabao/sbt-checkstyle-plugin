version := "0.1"

name := "checkstyle-integration-test"

organization := "com.etsy"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings ++ checkstyleSettings(IntegrationTest): _*)

// custom checkstyleConfigLocation & checkstyleOutputFile (optional)
checkstyleConfigLocation := baseDirectory.value / "my-checkstyle-config.xml"
// default is "checkstyle-it-report.xml"
checkstyleOutputFile in IntegrationTest := target.value / "checkstyle-integration-test-report.xml"
