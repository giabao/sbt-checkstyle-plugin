package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import com.etsy.sbt.checkstyle.CheckstyleSeverityLevel.CheckstyleSeverityLevel
import sbt.Def.Initialize
import sbt.Keys._
import sbt.io.{Path, PathFinder}
import sbt.util.NoJsonWriter
import sbt.{Def, HiddenFileFilter, _}

/**
  * An SBT plugin to run checkstyle over Java code
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  * @author Alejandro Rivera <alejandro.rivera.lopez@gmail.com>
  * @author Joseph Earl <joe@josephearl.co.uk>
  */
// scalastyle:off multiple.string.literals
object CheckstylePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    // https://github.com/sbt/sbt/issues/5049#issuecomment-528801726
    // scalastyle:off method.name
    private[this] def SettingKey[T: Manifest](label: String, description: String = "") =
      sbt.SettingKey[T](label, description)(implicitly, NoJsonWriter())
    // scalastyle:on method.name

    val CheckstyleLibs = config("CheckstyleLibs")

    val checkstyle = TaskKey[Int](
      "checkstyle",
      "Runs checkstyle, return num of issues has severity > CheckstyleSeverityLevel"
    )
    val checkstyleOutputFile =
      SettingKey[File]("checkstyle-target", "The location of the generated checkstyle report")
    val checkstyleHeaderLocation = SettingKey[File](
      "checkstyle-header-location",
      "The location of the header file. Similar to https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#headerLocation"
    )
    val checkstyleConfigLocation = taskKey[File]("The checkstyle XML configuration file")
    val checkstyleXsltTransformations = SettingKey[Option[Set[CheckstyleXSLTSettings]]](
      "xslt-transformations",
      "An optional set of XSLT transformations to be applied to the checkstyle output"
    )
    val checkstyleSeverityLevel = SettingKey[Option[CheckstyleSeverityLevel]](
      "checkstyle-severity-level",
      "Sets the severity levels which should fail the build"
    )

    val CheckstyleConfigLocation = com.etsy.sbt.checkstyle.CheckstyleConfigLocation
    val CheckstyleSeverityLevel  = com.etsy.sbt.checkstyle.CheckstyleSeverityLevel
    val CheckstyleXSLTSettings   = com.etsy.sbt.checkstyle.CheckstyleXSLTSettings

    /** Runs checkstyle
      * @param conf The configuration (Compile or Test) in which context to execute the checkstyle command */
    def checkstyleTask(conf: Configuration): Initialize[Task[Int]] = Def.taskDyn {
      val sourceFiles = (conf / checkstyle / sources).value
      if (sourceFiles.isEmpty) Def.task { 0 } else checkstyleTaskImpl(conf, sourceFiles)
    }

    def checkstyleSettings(c: Configuration): Seq[Setting[_]] = Seq(
      c / checkstyleOutputFile := target.value / s"checkstyle-${c.name}-report.xml",
      // see sbt.Defaults.sourceConfigPaths
      sourcesSetting(c),
      checkstyle := checkstyleTask(c).value
    )
  }

  // scalastyle:off import.grouping
  import autoImport._
  // scalastyle:on import.grouping

  override def projectConfigurations: Seq[Configuration] = Seq(CheckstyleLibs)

  // scalastyle:off method.length
  private def checkstyleTaskImpl(
      conf: Configuration,
      sourceFiles: Seq[File]
  ): Initialize[Task[Int]] = Def.task {
    val log = (checkstyle / streams).value.log

    val headerFileOpt = {
      val f = checkstyleHeaderLocation.value
      if (f.getName.isEmpty) {
        None
      } else if (f.exists()) {
        Some("checkstyle.header.file" -> f.getAbsolutePath)
      } else {
        log.warn(s"checkstyleHeaderLocation file not found: $f")
        None
      }
    }

    val forkOpts = (checkstyle / forkOptions).value
    val trap     = (checkstyle / trapExit).value
    val r: ScalaRun = if ((checkstyle / fork).value) {
      val newJVMOpts = forkOpts.runJVMOptions ++ headerFileOpt.map { case (k, v) => s"-D$k=$v" }
      new ForkRun(forkOpts.withRunJVMOptions(newJVMOpts))
    } else {
      headerFileOpt foreach { case (k, v) => sys.props(k) = v }
      new Run(clsLoader, trap)
    }

    val outputFile = (conf / checkstyleOutputFile).value
    val checkstyleOpts = Seq(
      // format: off
      "-c", (conf / checkstyleConfigLocation).value.getAbsolutePath, // checkstyle configuration file
      "-f", "xml", // output format
      "-o", outputFile.absolutePath // output file
      // format: on
    ) ++ sourceFiles.map(_.absolutePath)

    r.run(
      "com.puppycrawl.tools.checkstyle.Main",
      (CheckstyleLibs / managedClasspath).value.files,
      checkstyleOpts,
      Logger.Null
    )

    if (!outputFile.exists) {
      0
    } else {
      (conf / checkstyleXsltTransformations).value.foreach { xslt =>
        Checkstyle.applyXSLT(outputFile, xslt)
      }

      (conf / checkstyleSeverityLevel).value.map { severityLevel =>
        Checkstyle.processIssues(log, outputFile, severityLevel)
      } match {
        case Some(issuesFound) if issuesFound > 0 =>
          log.error(
            s"${name.value} / checkstyle: $issuesFound issue(s) found in Checkstyle report: $outputFile"
          )
          issuesFound
        case _ =>
          log.info(s"${name.value} / checkstyle success")
          0
      }
    }
  }
  // scalastyle:on method.length

  private[this] def clsLoader(paths: Seq[File]) =
    new URLClassLoader(Path.toURLs(paths), ClassLoader.getSystemClassLoader)

  private def sourcesSetting(c: Configuration) = c / checkstyle / sources := {
    val include = (c / checkstyle / includeFilter).value
    val exclude = (c / checkstyle / excludeFilter).value
    val filter  = include -- exclude
    (c / unmanagedSourceDirectories).value.flatMap { d =>
      PathFinder(d).globRecursive(filter).get()
    }
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    checkstyleXsltTransformations := None,
    checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Error),
    libraryDependencies += "com.puppycrawl.tools" % "checkstyle" % "8.29" % CheckstyleLibs,
    CheckstyleLibs / managedClasspath := Classpaths
      .managedJars(CheckstyleLibs, classpathTypes.value, update.value),
    checkstyle / fork := false,
    checkstyle / forkOptions := (Compile / forkOptions).value,
    checkstyle / trapExit := true,
    checkstyleHeaderLocation := file(""), // default `file("")` means checkstyleHeaderLocation will not be used
    checkstyleConfigLocation := (ThisBuild / baseDirectory).value / "checkstyle-config.xml",
    checkstyle / includeFilter := "*.java",
    checkstyle / excludeFilter := HiddenFileFilter,
    // default settings for ScopeAxis's configuration = Zero
    checkstyleOutputFile := target.value / "checkstyle-report.xml",
    sourcesSetting(Compile),
    checkstyle := checkstyleTask(Compile).value
  ) ++ checkstyleSettings(Test)
}
