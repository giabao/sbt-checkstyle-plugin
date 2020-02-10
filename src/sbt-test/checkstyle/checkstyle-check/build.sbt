version := "0.1"

name := "checkstyle-check"

organization := "com.etsy"

checkstyleConfigLocation := baseDirectory.value / "my-checkstyle-config.xml"
checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Error)
