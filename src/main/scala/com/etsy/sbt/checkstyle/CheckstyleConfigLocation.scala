package com.etsy.sbt.checkstyle

import java.net.URLClassLoader

import scala.xml.{Node, XML}

/**
  * Represents a Checkstyle XML configuration located locally, on the class path or remotely at a URL
  *
  * @author Joseph Earl
  */
sealed abstract class CheckstyleConfigLocation(val location: String) {
  def read(): xml.Node
}

object CheckstyleConfigLocation {
  case class URL(url: String) extends CheckstyleConfigLocation(url) {
    override def read(): Node = XML.load(url)
  }

  case class File(path: String) extends CheckstyleConfigLocation(path) {
    override def read(): Node = XML.loadFile(path)
  }

  case class Classpath(name: String, cp: sbt.Def.Classpath)
      extends CheckstyleConfigLocation(name) {
    override def read(): Node = {
      val classpath = cp.map(_.data.toURI.toURL)
      val loader    = new URLClassLoader(classpath.toArray, getClass.getClassLoader)
      val is        = loader.getResourceAsStream(name)
      XML.load(is)
    }
  }
}
