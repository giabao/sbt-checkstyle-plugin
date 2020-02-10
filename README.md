# sbt-checkstyle-plugin [![Build Status](https://travis-ci.org/giabao/sbt-checkstyle-plugin.svg?branch=master)](https://travis-ci.org/giabao/sbt-checkstyle-plugin)

This project provides an SBT 0.13+ and 1.x plugin for running Checkstyle over
Java source files.  For more information about Checkstyle, see
[http://checkstyle.sourceforge.net/](http://checkstyle.sourceforge.net/)

This plugin uses version 8.29 of Checkstyle.

This is a fork of the sbt-code-quality project found
[here](https://github.com/corux/sbt-code-quality).

## Setup

Add the following lines to `project/plugins.sbt`:

```scala
addSbtPlugin("com.sandinh" % "sbt-checkstyle" % "3.1.2")
```

sbt-checkstyle is an AutoPlugin, so there is no need to modify the `build.sbt` file to enable it.

## Usage

You can run Checkstyle over your Java source files with the
`checkstyle` task.  You can run Checkstyle over your Java tests with
the `test:checkstyle` task.

The Checkstyle configuration file is `./checkstyle-config.xml` by
default.  This can be changed by setting the value of
`checkstyleConfigLocation`.  By default `test:checkstyle` uses the same
configuration file, but this can be changed by setting the value of
`checkstyleConfigLocation in Test`.

The Checkstyle report is output to `target/checkstyle-report.xml` by
default.  This can be changed by setting the value of
`checkstyleOutputFile`.  `test:checkstyle` outputs to
`target/checkstyle-test-report.xml`, but this can be changed by
setting the value of `checkstyleOutputFile in Test`.

To change the checkstyle configuration file set `checkstyleConfigLocation` in `build.sbt`:
```scala
checkstyleConfigLocation := CheckstyleConfigLocation.File("checkstyle-config.xml")
```

You can also load remote configuration files by specifying a URL:
```scala
checkstyleConfigLocation :=
  CheckstyleConfigLocation.URL("https://raw.githubusercontent.com/checkstyle/checkstyle/master/config/checkstyle_checks.xml")
```

Or load configuration files from the classpath by specifying a resource name:
```scala
checkstyleConfigLocation := CheckstyleConfigLocation.Classpath("com/etsy/checkstyle-config.xml")
```

To run Checkstyle automatically after compilation:
```scala
(checkstyle in Compile) := (checkstyle in Compile).triggeredBy(compile in Compile).value
```

To run Checkstyle automatically after test compilation:
```scala
(checkstyle in Test) := (checkstyle in Test).triggeredBy(compile in Test).value
```

### XSLT transformations

The `checkstyleXsltTransformations` setting allows applying XSLT transformations to the XML report generated by Checkstyle. For instance, this could be used to generate a more readable HTML report.  This setting takes values of `Option[Set[XSLTSettings]]`, so multiple transformations can be applied.

You can set `checkstyleXsltTransformations` like so in `build.sbt`:
```scala
checkstyleXsltTransformations := {
  Some(Set(CheckstyleXSLTSettings(baseDirectory(_ / "checkstyle-noframes.xml").value, target(_ / "checkstyle-report.html").value)))
}
```

### Failing the build

You can control what severity of issues should break the build by setting the `checkstyleSeverityLevel` in your `build.sbt` as follows:
```scala
checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Error)
```

Possible values are defined by the `CheckstyleSeverityLevel` enumeration. The default is `None`.

### Integration tests

If you want to run Checkstyle on your integration tests add the following to your `build.sbt`:
```scala
lazy val root = (project in file(".")).configs(IntegrationTest)

Defaults.itSettings

checkstyleConfigLocation := CheckstyleConfigLocation.File("my-checkstyle-config.xml"),
checkstyle in IntegrationTest := checkstyleTask(IntegrationTest).value,
checkstyleOutputFile in IntegrationTest := target.value / "checkstyle-integration-test-report.xml"
```

You can then run the tasks `it:checkstyle` and `it:checkstyle-check`.

### Upgrading Checkstyle version

SBT Checkstyle plugin comes with a default Checkstyle version: currently, Checkstyle 6.15 is used by default.

Provided the new Checkstyle version is compatible, you can override the version used at runtime in your `project/plugins.sbt`:

```scala
dependencyOverrides += "com.puppycrawl.tools" % "checkstyle" % "6.15"
```

## Settings

### `checkstyleOutputFile`
* *Description:* The location of the generated checkstyle report.
* *Accepts:* any legal file path
* *Default:* `Some(target.value / "checkstyle-report.xml")`

### `checkstyleConfigLocation`
* *Description:* The location of the checkstyle configuration file.
* *Accepts:* `CheckstyleConfigLocation.{File, URL, Classpath}`
* *Default:* `CheckstyleConfigLocation.File("checkstyle-config.xml")`

### `checkstyleXsltTransformations`
* *Description:* A set of XSLT transformations to be applied to the checkstyle output (optional).
* *Accepts:* `Some(Set[CheckstyleXSLTSettings])`
* *Default:* `None`

### `checkstyleSeverityLevel`
* *Description:* Decide how much effort to put into analysis.
* *Accepts:* `Some(CheckstyleSeverityLevel.{Ignore, Info, Warning, Error})`
* *Default:* `None`

## dev guide
+ clone
+ using IntelliJ
+ Set IntelliJ using scalafmt code formatter
+ sbt
```sbtshell
test
scalastyle
scripted
```
+ publish:
https://www.scala-sbt.org/1.x/docs/Bintray-For-Plugins.html
