package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import com.etsy.sbt.checkstyle.CheckstyleSeverityLevel.CheckstyleSeverityLevel
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
import sbt.io.Path
import sbt.util.NoJsonWriter

/**
  * An SBT plugin to run checkstyle over Java code
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  * @author Alejandro Rivera <alejandro.rivera.lopez@gmail.com>
  * @author Joseph Earl <joe@josephearl.co.uk>
  */
object CheckstylePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    // https://github.com/sbt/sbt/issues/5049#issuecomment-528801726
    // scalastyle:off method.name
    private[this] def SettingKey[T: Manifest](label: String, description: String = "") =
      sbt.SettingKey[T](label, description)(implicitly, NoJsonWriter())
    // scalastyle:on method.name

    val CheckstyleLibs = config("CheckstyleLibs")

    val checkstyle = TaskKey[Unit]("checkstyle", "Runs checkstyle")
    val checkstyleOutputFile = SettingKey[File]("checkstyle-target", "The location of the generated checkstyle report")
    val checkstyleHeaderLocation = SettingKey[File](
      "checkstyle-header-location",
      "The location of the header file. Similar to https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html#headerLocation"
    )
    val checkstyleConfigLocation = taskKey[File]("The checkstyle XML configuration file")
    val checkstyleXsltTransformations = SettingKey[Option[Set[CheckstyleXSLTSettings]]]("xslt-transformations", "An optional set of XSLT transformations to be applied to the checkstyle output")
    val checkstyleSeverityLevel = SettingKey[Option[CheckstyleSeverityLevel]]("checkstyle-severity-level", "Sets the severity levels which should fail the build")

    val CheckstyleConfigLocation = com.etsy.sbt.checkstyle.CheckstyleConfigLocation
    val CheckstyleSeverityLevel = com.etsy.sbt.checkstyle.CheckstyleSeverityLevel
    val CheckstyleXSLTSettings = com.etsy.sbt.checkstyle.CheckstyleXSLTSettings

    /**
      * Runs checkstyle
      *
      * @param conf The configuration (Compile or Test) in which context to execute the checkstyle command
      */
    def checkstyleTask(conf: Configuration): Initialize[Task[Unit]] = Def.task {
      val log = (conf / checkstyle / streams).value.log

      val headerFileOpt = {
        val f = checkstyleHeaderLocation.value
        if (f.getName == "") {
          None
        } else if (f.exists()) {
          Some("checkstyle.header.file" -> f.getAbsolutePath)
        } else {
          log.warn(s"checkstyleHeaderLocation file not found: $f")
          None
        }
      }

      val forkOpts = (checkstyle / forkOptions).value
      val trap = (checkstyle / trapExit).value
      val r: ScalaRun = if ((checkstyle / fork).value) {
        val newJVMOpts = forkOpts.runJVMOptions ++ headerFileOpt.map { case (k, v) => s"-D$k=$v" }
        new ForkRun(forkOpts.withRunJVMOptions(newJVMOpts))
      } else {
        headerFileOpt foreach { case (k, v) => sys.props(k) = v }
        new Run(clsLoader, trap)
      }

      val outputFile = (conf / checkstyleOutputFile).value
      val checkstyleOpts = Seq(
        "-c", (conf / checkstyleConfigLocation).value.getAbsolutePath, // checkstyle configuration file
        "-f", "xml", // output format
        "-o", outputFile.absolutePath, // output file
        (conf / javaSource).value.absolutePath, // location of Java source file
      )

      r.run(
        "com.puppycrawl.tools.checkstyle.Main",
        (CheckstyleLibs / managedClasspath).value.files,
        checkstyleOpts,
        Logger.Null
      )

      if (outputFile.exists) {
        (conf / checkstyleXsltTransformations).value.foreach { xslt =>
          Checkstyle.applyXSLT(outputFile, xslt)
        }

        (conf / checkstyleSeverityLevel).value.map { severityLevel =>
          Checkstyle.processIssues(log, outputFile, severityLevel)
        } match {
          case Some(issuesFound) if issuesFound > 0 =>
            log.error(s"$issuesFound issue(s) found in Checkstyle report: $outputFile")
          case _ =>
            log.info(s"Checkstyle success")
        }
      }
    }
    private[this] def clsLoader(paths: Seq[File]) = new URLClassLoader(Path.toURLs(paths), ClassLoader.getSystemClassLoader)
  }

  // scalastyle:off import.grouping
  import autoImport._
  // scalastyle:on import.grouping

  override def projectConfigurations = Seq(CheckstyleLibs)

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    checkstyleXsltTransformations := None,
    checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Error),
    libraryDependencies += "com.puppycrawl.tools"     % "checkstyle"   % "8.29" % CheckstyleLibs,
    CheckstyleLibs / managedClasspath := Classpaths.managedJars(CheckstyleLibs, classpathTypes.value, update.value),
    checkstyle / fork := false,
    checkstyle / forkOptions := (Compile / forkOptions).value,
    checkstyle / trapExit := true,
    checkstyleOutputFile := target.value / "checkstyle-report.xml",
    checkstyleOutputFile in Test := target.value / "checkstyle-test-report.xml",
    checkstyleHeaderLocation := file(""), // default `file("")` means checkstyleHeaderLocation will not be used
    checkstyleConfigLocation := (ThisBuild / baseDirectory).value / "checkstyle-config.xml",
    checkstyleConfigLocation in Test := checkstyleConfigLocation.value,
    checkstyle := checkstyleTask(Compile).value,
    checkstyle in Test := checkstyleTask(Test).value
  )
}
