version := "0.1"

name := "checkstyle-test"

organization := "com.etsy"

checkstyleConfigLocation := baseDirectory.value / "checkstyle-config.xml"

(checkstyle in Compile) := (checkstyle in Compile).triggeredBy(compile in Compile).value
