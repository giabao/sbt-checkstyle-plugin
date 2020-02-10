package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import sbt.Def.Initialize
import sbt.Keys._
import sbt.{Def, _}

import scala.xml.XML
import scala.xml.dtd.{DocType, PublicID}

/**
  * Represents a Checkstyle XML configuration located locally, on the class path or remotely at a URL
  *
  * @author Joseph Earl
  */
object CheckstyleConfigLocation {
  @deprecated("use a file setting directly, ex baseDirectory.value / \"some_path\"", "3.2.0")
  // scalastyle:off method.name
  def File(path: String): Initialize[File] = Def.setting[File] {
    (ThisBuild / baseDirectory).value / path
  }

  def URL(url: String): Initialize[Task[File]] = Def.task[File] {
    save(XML.load(sbt.url(url)), target.value)
  }

  def Classpath(name: String, classpath: TaskKey[Classpath]): Initialize[Task[File]] =
    Def.task[File] {
      resource(name, target.value, classpath.value)
    }

  def Classpath(name: String): Initialize[Task[File]] = Def.task[File] {
    resource(name, target.value)
  }
  // scalastyle:on method.name

  private def resource(name: String, target: File, classpath: Classpath = null) = {
    val loader =
      if (classpath == null) getClass.getClassLoader
      else {
        val cp = classpath.map(_.data.toURI.toURL)
        new URLClassLoader(cp.toArray, getClass.getClassLoader)
      }
    val is = loader.getResourceAsStream(name)
    save(XML.load(is), target)
  }

  private def save(config: xml.Elem, targetFolder: File) = {
    val configFile = targetFolder / "checkstyle-config.xml"
    XML.save(
      configFile.getAbsolutePath,
      config,
      "UTF-8",
      xmlDecl = true,
      DocType(
        "module",
        PublicID(
          "-//Puppy Crawl//DTD Check Configuration 1.3//EN",
          "https://checkstyle.org/dtds/configuration_1_3.dtd"
        ),
        Nil
      )
    )
    configFile
  }
}
