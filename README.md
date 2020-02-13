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
checkstyleConfigLocation := baseDirectory.value / "checkstyle-config.xml"
```

You can also load remote configuration files by specifying a URL:
```scala
checkstyleConfigLocation := CheckstyleConfigLocation.URL(
  "https://raw.githubusercontent.com/checkstyle/checkstyle/master/config/checkstyle_checks.xml"
).value
```

Or load configuration files from the classpath by specifying a resource name and an optional ClassPath:
```scala
checkstyleConfigLocation := CheckstyleConfigLocation.Classpath("com/etsy/checkstyle-config.xml").value
// or
checkstyleConfigLocation := CheckstyleConfigLocation.Classpath(
  "google_checks.xml", // google_checks.xml is in com.puppycrawl.tools:checkstyle:<version> jar file
  Compile / managedClasspath
).value
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
```sbt
lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings ++ checkstyleSettings(IntegrationTest): _*)
```

You can then run the tasks `it:checkstyle` and `it:checkstyle-check`.

### Upgrading Checkstyle version

SBT Checkstyle plugin comes with a default Checkstyle version: currently, Checkstyle 8.29 is used by default.

Provided the new Checkstyle version is compatible, you can override the version used at runtime in your `build.sbt`:

```sbt
dependencyOverrides += "com.puppycrawl.tools" % "checkstyle" % "8.29" % CheckstyleLibs
```

## Settings

### `checkstyleOutputFile`
* *Description:* The location of the generated checkstyle report.
* *Accepts:* any legal file path
* *Default:* `Some(target.value / "checkstyle-report.xml")`

### `checkstyleConfigLocation`
* *Description:* The location of the checkstyle configuration file.
* *Accepts:* `File`, ex: `baseDirectory.value / "checkstyle-config.xml"`
 or use one of CheckstyleConfigLocation's method: `URL(url: String)` | `Classpath(name: String, classpath: Classpath = (Compile / fullClasspath).value}`
* *Default:* `checkstyle-config.xml` file in root project

### `checkstyleXsltTransformations`
* *Description:* A set of XSLT transformations to be applied to the checkstyle output (optional).
* *Accepts:* `Some(Set[CheckstyleXSLTSettings])`
* *Default:* `None`

### `checkstyleSeverityLevel`
* *Description:* Decide how much effort to put into analysis.
* *Accepts:* `Some(CheckstyleSeverityLevel.{Ignore, Info, Warning, Error})`
* *Default:* `None`

### `checkstyleHeaderFile`
Similar to maven-checkstyle-plugin's [headerLocation param](https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#headerLocation)

### `checkstyleProperties`
Properties correspond to `-p` param of [checkstyle cli](https://checkstyle.sourceforge.io/cmdline.html#Command_line_usage)

### `checkstyleRunOpts`
` = taskKey[Seq[String]]("options to pass to checkstyle cli")`
Usage example: `checkstyleRunOpts += "--debug"`

### `checkstyle / {fork, forkOptions, trapExit, runner}`
To control how to run upstream checkstyle cli

### `checkstyle / {includeFilter, excludeFilter, sources}`
To control source files in checkstyle task

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

## changelogs
#### 3.2.0
+ Change organization & name from `"com.etsy" % "sbt-checkstyle-plugin"` to `"com.sandinh" % "sbt-checkstyle"`
+ `sbt-checkstyle` is now published to bintray as in [this guide](https://www.scala-sbt.org/1.x/docs/Bintray-For-Plugins.html)
+ Update default version of checkstyle from 6.15 to 8.29
+ Drop support for sbt 0.13.x
+ break change: `checkstyleConfigLocation` is now a `TaskKey[File]`, not `SettingKey[CheckstyleConfigLocation]`
  and `CheckstyleConfigLocation.{File, URL, Classpath}` now return a Setting instead of a pure value.
  
  Migrate:
  ```sbt
  checkstyleConfigLocation := CheckstyleConfigLocation.File("path")
  checkstyleConfigLocation := CheckstyleConfigLocation.URL("url")
  checkstyleConfigLocation := CheckstyleConfigLocation.Classpath("name")
  ```
  =>
  ```sbt
  checkstyleConfigLocation := baseDirectory.value / "path"
  checkstyleConfigLocation := CheckstyleConfigLocation.URL("url").value
  checkstyleConfigLocation := CheckstyleConfigLocation.Classpath("name").value
  ```
+ Fix CheckstyleConfigLocation.Classpath (`checkstyle-config-classpath` sbt-test failed)
+ Add `CheckstyleConfigLocation.Classpath(path/to/resource, a-classpath)`.
  For example, `a-classpath` can be `(Compile / exportedProducts).value`
+ Call `sys.error` instead of `sys.exit` when `checkstyleSeverityLevel.isDefined` && checkstyle found issues has `severity > checkstyleSeverityLevel`
+ Change the way to [Upgrading Checkstyle version](#upgrading-checkstyle-version)
+ Add `autoImport.checkstyleSettings` for using with other configurations such as [Integration tests](#integration-tests)
  
  Migrate:
  ```sbt
  lazy val root = (project in file(".")).configs(IntegrationTest)
  Defaults.itSettings
  
  checkstyleConfigLocation := baseDirectory.value / "my-checkstyle-config.xml"
  checkstyle in IntegrationTest := checkstyleTask(IntegrationTest).value
  checkstyleOutputFile in IntegrationTest := target.value / "checkstyle-integration-test-report.xml"
  ```
  =>
  ```sbt
  lazy val root = (project in file("."))
    .configs(IntegrationTest)
    .settings(Defaults.itSettings ++ checkstyleSettings(IntegrationTest): _*)
  
  // custom checkstyleConfigLocation & checkstyleOutputFile (optional)
  checkstyleConfigLocation := baseDirectory.value / "my-checkstyle-config.xml"
  checkstyleOutputFile in IntegrationTest := target.value / "checkstyle-integration-test-report.xml"
  ```
  
+ Add settings:
 - `checkstyleHeaderFile, checkstyleProperties, checkstyleRunOpts`
 - `checkstyle / {fork, forkOptions, trapExit, runner}`
 - `checkstyle / {includeFilter, excludeFilter, sources}`
