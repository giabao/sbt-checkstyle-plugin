package com.etsy.sbt.checkstyle

import org.scalatestplus.junit.JUnitSuite
import org.junit.Assert._
import org.junit.Test
import org.junit.Rule
import Checkstyle._
import scala.annotation.meta.getter
import org.junit.contrib.java.lang.system.ExpectedSystemExit

class CheckstyleSuite extends JUnitSuite {
  @(Rule @getter)
  def exitRule = ExpectedSystemExit.none()

  @Test
  def testNoExit() = {
    val originalSecManager = System.getSecurityManager
    noExit {
      sys.exit(1)
    }
    assertEquals(
      "Security manager changed after execution",
      originalSecManager,
      System.getSecurityManager
    )
  }
}
