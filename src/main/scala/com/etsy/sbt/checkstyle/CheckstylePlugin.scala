package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import com.etsy.sbt.checkstyle.CheckstyleSeverityLevel.CheckstyleSeverityLevel
import sbt.Def.Initialize
import sbt.Keys._
import sbt.io.{Path, PathFinder}
import sbt.util.NoJsonWriter
import sbt.{Def, HiddenFileFilter, _}

import scala.util.{Failure, Success}

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

    val checkstyle = taskKey[Unit]("Runs checkstyle")
    val checkstyleOutputFile =
      SettingKey[File]("checkstyleOutputFile", "The location of the generated checkstyle report")
    val checkstyleHeaderFile = SettingKey[File](
      "checkstyleHeaderFile",
      "The header file. Similar to https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#headerLocation"
    )
    val checkstyleProperties = taskKey[Map[String, String]]("Properties correspond to `-p` param of checkstyle cli")
    val checkstyleRunOpts = taskKey[Seq[String]]("options to pass to checkstyle cli")
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
      * @param c The configuration (Compile or Test) in which context to execute the checkstyle command
      * @see [[https://www.scala-sbt.org/1.x/docs/Tasks.html#Dynamic+Computations+with Dynamic Computations with Def.taskDyn]] */
    def checkstyleTask(c: Configuration): Initialize[Task[Unit]] = Def.taskDyn {
      val sourceFiles = (c / checkstyle / sources).value
      if (sourceFiles.isEmpty) {
        Def.task { }
      } else {
        checkstyleRunTask(c)
      }
    }

    private def checkstyleRunTask(c: Configuration) = Def.task {
      val log = (checkstyle / streams).value.log

      val r: ScalaRun = (checkstyle / runner).value
      val classpath = (CheckstyleLibs / managedClasspath).value.files
      val runOpts = (c / checkstyleRunOpts).value
      r.run("com.puppycrawl.tools.checkstyle.Main", classpath, runOpts, Logger.Null)

      // checkstylePostProcessTask: xslt transform & count issues
      val outputFile = (c / checkstyleOutputFile).value
      val transformerOpt = (c / checkstyleXsltTransformations).value
      val severityOpt = (c / checkstyleSeverityLevel).value

      // for logging purpose only
      val baseDir = baseDirectory.value
      val isRoot = baseDir == (ThisBuild / baseDirectory).value
      val id = name.value
      val logPrefix = if (isRoot) "checkstyle" else s"$id / checkstyle"
      def rel(f: File) = f.relativeTo(baseDir).getOrElse(f)

      if (outputFile.exists) {
        transformerOpt.foreach { Checkstyle.applyXSLT(outputFile, _) }
        severityOpt.map { Checkstyle.processIssues(log, logPrefix, baseDir, outputFile, _) } match {
          case None =>
            log.info(s"$logPrefix done. See report at ${rel(outputFile)} or set `checkstyleSeverityLevel := Some(Error)` to see errors in console")
          case Some(0) =>
            log.info(s"$logPrefix success. No issues has SeverityLevel >= '${severityOpt.get}'")
          case Some(issuesFound) =>
            // fail the build
            sys.error(s"$logPrefix: $issuesFound issue(s) found in Checkstyle report ${rel(outputFile)}")
        }
      }
    }

    def checkstyleSettings(c: Configuration): Seq[Setting[_]] = Seq(
      c / checkstyleOutputFile := target.value / s"checkstyle-${c.name}-report.xml",
      c / checkstyle / sources := sourcesTask(c).value,
      c / checkstyleRunOpts := runOptsTask(c).value,
      c / checkstyle := checkstyleTask(c).value
    )
  }

  // scalastyle:off import.grouping
  import autoImport._
  // scalastyle:on import.grouping

  override def projectConfigurations: Seq[Configuration] = Seq(CheckstyleLibs)

  private[this] def clsLoader(paths: Seq[File]) =
    new URLClassLoader(Path.toURLs(paths), ClassLoader.getSystemClassLoader)

  /** @see [[sbt.Defaults.sourceConfigPaths]] */
  private def sourcesTask(c: Configuration) = Def.task {
    val include = (c / checkstyle / includeFilter).value
    val exclude = (c / checkstyle / excludeFilter).value
    val filter  = include -- exclude
    (c / unmanagedSourceDirectories).value.flatMap { d =>
      PathFinder(d).globRecursive(filter).get()
    }
  }
  /** arguments pass to run [[com.puppycrawl.tools.checkstyle.Main.main]] */
  private def runOptsTask(c: Configuration) = Def.task {
    val sourceFiles = (c / checkstyle / sources).value
    val configFile = (c / checkstyleConfigLocation).value
    val outputFile = (c / checkstyleOutputFile).value
    Seq(
      // format: off
      "-c", configFile.getAbsolutePath, // checkstyle configuration file
      "-f", "xml", // output format
      "-o", outputFile.absolutePath // output file
      // format: on
    ) ++ sourceFiles.map(_.absolutePath)
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    checkstyleXsltTransformations := None,
    checkstyleSeverityLevel := None,
    libraryDependencies += "com.puppycrawl.tools" % "checkstyle" % "8.29" % CheckstyleLibs,
    CheckstyleLibs / managedClasspath := Classpaths
      .managedJars(CheckstyleLibs, classpathTypes.value, update.value),
    checkstyle / fork := true,
    checkstyleProperties := {
      val f = checkstyleHeaderFile.value
      if (f.name.isEmpty) {
        Map.empty
      } else if (f.exists) {
        Map("checkstyle.header.file" -> f.getAbsolutePath)
      } else {
        sys.error(s"checkstyleHeaderFile not found: $f")
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
    // default settings for ScopeAxis's configuration = Zero - see [[checkstyleSettings]]
    checkstyleOutputFile := target.value / "checkstyle-report.xml",
    checkstyle / sources := sourcesTask(Compile).value,
    checkstyleRunOpts := runOptsTask(Compile).value,
    checkstyle := checkstyleTask(Compile).value
  ) ++ checkstyleSettings(Test)
}
