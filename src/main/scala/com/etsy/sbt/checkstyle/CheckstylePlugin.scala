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

    val checkstyle = taskKey[Int]("Runs checkstyle, return output file")
    val checkstyleOutputFile =
      SettingKey[File]("checkstyleOutputFile", "The location of the generated checkstyle report")
    val checkstyleHeaderFile = SettingKey[File](
      "checkstyleHeaderLocation",
      "The location of the header file. Similar to https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#headerLocation"
    )
    val checkstyleProperties = taskKey[Map[String, String]]("Properties correspond to `-p` param of checkstyle cli")
    val checkstyleConfigLocation = taskKey[File]("The checkstyle XML configuration file")
    val checkstyleXsltTransformations = SettingKey[Option[Set[CheckstyleXSLTSettings]]](
      "checkstyleXsltTransformations",
      "An optional set of XSLT transformations to be applied to the checkstyle output"
    )
    val checkstyleSeverityLevel = SettingKey[Option[CheckstyleSeverityLevel]](
      "checkstyleSeverityLevel",
      "Sets the severity levels which should fail the build"
    )

    val CheckstyleConfigLocation = com.etsy.sbt.checkstyle.CheckstyleConfigLocation
    val CheckstyleSeverityLevel  = com.etsy.sbt.checkstyle.CheckstyleSeverityLevel
    val CheckstyleXSLTSettings   = com.etsy.sbt.checkstyle.CheckstyleXSLTSettings

    /** Runs checkstyle
      * @param conf The configuration (Compile or Test) in which context to execute the checkstyle command */
    def checkstyleTask(conf: Configuration): Initialize[Task[Int]] = Def.task {
      val sourceFiles = (conf / checkstyle / sources).value
      val r: ScalaRun = (checkstyle / runner).value
      val outputFile = (conf / checkstyleOutputFile).value
      val configFile = (conf / checkstyleConfigLocation).value
      val classpath = (CheckstyleLibs / managedClasspath).value.files

      if (sourceFiles.nonEmpty) {
        val checkstyleOpts = Seq(
          // format: off
          "-c", configFile.getAbsolutePath, // checkstyle configuration file
          "-f", "xml", // output format
          "-o", outputFile.absolutePath // output file
          // format: on
        ) ++ sourceFiles.map(_.absolutePath)
        r.run(
          "com.puppycrawl.tools.checkstyle.Main",
          classpath,
          checkstyleOpts,
          Logger.Null
        )
      }
      checkstylePostProcessTask(conf).value
    }

    /** xslt transform & count issues */
    private def checkstylePostProcessTask(conf: Configuration) = Def.task {
      val outputFile = (conf / checkstyleOutputFile).value
      val log = (checkstyle / streams).value.log

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

    def checkstyleSettings(c: Configuration): Seq[Setting[_]] = Seq(
      c / checkstyleOutputFile := target.value / s"checkstyle-${c.name}-report.xml",
      c / checkstyle / sources := sourcesTask(c).value,
      c / checkstyle := checkstyleTask(c).value
    )
  }

  // scalastyle:off import.grouping
  import autoImport._
  // scalastyle:on import.grouping

  override def projectConfigurations: Seq[Configuration] = Seq(CheckstyleLibs)

  private[this] def clsLoader(paths: Seq[File]) =
    new URLClassLoader(Path.toURLs(paths), ClassLoader.getSystemClassLoader)

  /** @ee [[sbt.Defaults.sourceConfigPaths]] */
  private def sourcesTask(c: Configuration) = Def.task {
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
    checkstyle / fork := true,
    checkstyleProperties := {
      val log = streams.value.log
      val f = checkstyleHeaderFile.value
      if (f.name.isEmpty) {
        Map.empty
      } else if (f.exists) {
        Map("checkstyle.header.file" -> f.getAbsolutePath)
      } else {
        log.warn(s"checkstyleHeaderLocation file not found: $f")
        Map.empty
      }
    },
    checkstyle / forkOptions := (Compile / forkOptions).value,
    checkstyle / runner := {
      val props = checkstyleProperties.value
      val trap = (checkstyle / trapExit).value
      val forkOpts = (checkstyle / forkOptions).value
      if ((checkstyle / fork).value) {
        val jvmOpts = forkOpts.runJVMOptions ++ props.map { case (k, v) => s"-D$k=$v" }
        new ForkRun(forkOpts.withRunJVMOptions(jvmOpts))
      } else {
        props.foreach { case (k, v) => sys.props(k) = v }
        new Run(clsLoader, trap)
      }
    },
    checkstyle / trapExit := true,
    checkstyleHeaderFile := file(""), // default `file("")` means checkstyleHeaderFile will not be used
    checkstyleConfigLocation := (ThisBuild / baseDirectory).value / "checkstyle-config.xml",
    checkstyle / includeFilter := "*.java",
    checkstyle / excludeFilter := HiddenFileFilter,
    // default settings for ScopeAxis's configuration = Zero
    checkstyleOutputFile := target.value / "checkstyle-report.xml",
    checkstyle / sources := sourcesTask(Compile).value,
    checkstyle := checkstyleTask(Compile).value
  ) ++ checkstyleSettings(Test)
}
