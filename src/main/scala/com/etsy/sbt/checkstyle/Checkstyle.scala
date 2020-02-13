package com.etsy.sbt.checkstyle

import javax.xml.transform.stream.StreamSource
import com.etsy.sbt.checkstyle.CheckstyleSeverityLevel._
import net.sf.saxon.s9api.Processor
import sbt._
import sbt.util.Logger

import scala.xml.XML

/**
  * A Scala wrapper around the Checkstyle Java API
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  * @author Joseph Earl <joe@josephearl.co.uk>
  */
object Checkstyle {
  /**
    * Processes style issues found by Checkstyle, returning a count of the number of issues
    *
    * @param log The SBT Logger
    * @param outputFile The Checkstyle report output path.
    * @param severityLevel The severity level at which to fail the build if style issues exist at that level
    * @return A count of the total number of issues processed
    */
  def processIssues(log: Logger, logPrefix: String, baseDir: File, outputFile: File, severityLevel: CheckstyleSeverityLevel): Int = {
    def rel(f: String) = file(f).relativeTo(baseDir).map(_.getPath).getOrElse(f)

    val report = XML.loadFile(outputFile)
    (report \ "file").flatMap { file =>
      (file \ "error").map { error =>
        val severity = CheckstyleSeverityLevel.withName(error \@"severity")
        if (severityLevel > severity) {
          0
        } else {
          val lineNumber = error \@ "line"
          val filename = file \@ "name"
          val errorMessage = error \@ "message"
          log.error(s"$logPrefix: $severity found in ${rel(filename)}:$lineNumber: $errorMessage")
          1
        }
      }
    }.sum
  }

  /**
    * Applies a set of XSLT transformation to the XML file produced by checkstyle
    *
    * @param input The XML file produced by checkstyle
    * @param transformations The XSLT transformations to be applied
    */
  def applyXSLT(input: File, transformations: Set[CheckstyleXSLTSettings]): Unit = {
    val processor = new Processor(false)
    val source = processor.newDocumentBuilder().build(input)

    transformations foreach { transform: CheckstyleXSLTSettings =>
      val output = processor.newSerializer(transform.output)
      val compiler = processor.newXsltCompiler()
      val executor = compiler.compile(new StreamSource(transform.xslt))
      val transformer = executor.load()
      transformer.setInitialContextNode(source)
      transformer.setDestination(output)
      transformer.transform()
      transformer.close()
      output.close()
    }
  }
}
