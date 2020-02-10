package com.etsy.sbt.checkstyle

import com.etsy.sbt.checkstyle.CheckstyleSeverityLevel.CheckstyleSeverityLevel
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
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
      Checkstyle.checkstyle(
        (javaSource in conf).value,
        (checkstyleOutputFile in conf).value,
        checkstyleHeaderLocation.value,
        (checkstyleConfigLocation in conf).value.getAbsolutePath,
        (checkstyleXsltTransformations in conf).value,
        (checkstyleSeverityLevel in conf).value,
        streams.value
      )
    }
  }

  // scalastyle:off import.grouping
  import autoImport._
  // scalastyle:on import.grouping

  private lazy val commonSettings: Seq[Def.Setting[_]] = Seq(
    checkstyleXsltTransformations := None,
    checkstyleSeverityLevel := None
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    checkstyleOutputFile := target.value / "checkstyle-report.xml",
    checkstyleOutputFile in Test := target.value / "checkstyle-test-report.xml",
    checkstyleHeaderLocation := file(""), // default `file("")` means checkstyleHeaderLocation will not be used
    checkstyleConfigLocation := (ThisBuild / baseDirectory).value / "checkstyle-config.xml",
    checkstyleConfigLocation in Test := checkstyleConfigLocation.value,
    checkstyle := checkstyleTask(Compile).value,
    checkstyle in Test := checkstyleTask(Test).value
  ) ++ commonSettings
}
