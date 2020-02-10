def props(names: String*) = names.flatMap(n => sys.props.get(s"plugin.$n"))

props("org", "version") match {
  case Seq(o, v) => addSbtPlugin(o % "sbt-checkstyle" % v)
  case _ =>
    sys.error(
      """|The system property 'plugin.version', 'plugin.org' are not defined.
         |Specify those properties using the scriptedLaunchOpts -D.""".stripMargin
    )
}
