package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import sbt.Def.Initialize
import sbt.Keys._
import sbt._

import scala.xml.XML

/**
  * Represents a Checkstyle XML configuration located locally, on the class path or remotely at a URL
  *
  * @author Joseph Earl
  */
object CheckstyleConfigLocation {
  @deprecated("use a file setting directly, ex baseDirectory.value / \"some_path\"", "3.2.0")
  def File(path: String): Initialize[Task[File]] = sbt.Def.task[File] {
    (ThisBuild / baseDirectory).value / path
  }

  def URL(url: String): Initialize[Task[File]] = sbt.Def.task[File] {
    save(XML.load(sbt.url(url)), target.value)
  }

  def Classpath(name: String, classpath: Classpath): Initialize[Task[File]] =
    sbt.Def.task[File] {
      resource(name, classpath, target.value)
    }

  def Classpath(name: String): Initialize[Task[File]] =
    sbt.Def.task[File] {
      resource(name, (Compile / fullClasspath).value, target.value)
    }

  private def resource(name: String, classpath: Classpath, target: File) = {
    val cp     = classpath.map(_.data.toURI.toURL)
    val loader = new URLClassLoader(cp.toArray, getClass.getClassLoader)
    val is     = loader.getResourceAsStream(name)
    XML.load(is)
    save(XML.load(is), target)
  }

  private def save(config: xml.Elem, targetFolder: File) = {
    val configFile = targetFolder / "checkstyle-config.xml"
    scala.xml.XML.save(
      configFile.getAbsolutePath,
      config,
      "UTF-8",
      xmlDecl = true,
      scala.xml.dtd.DocType(
        "module",
        scala.xml.dtd.PublicID(
          "-//Puppy Crawl//DTD Check Configuration 1.3//EN",
          "https://checkstyle.org/dtds/configuration_1_3.dtd"
        ),
        Nil
      )
    )
    configFile
  }
}
