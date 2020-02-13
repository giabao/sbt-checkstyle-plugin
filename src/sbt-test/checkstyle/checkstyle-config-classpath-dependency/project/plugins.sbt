import scala.util.{Success, Try}

def plugin: Try[ModuleID] = Try {
  val Array(o, n, v) = sys.props("plugin.version").split(':')
  o % n % v
}

plugin match {
  case Success(m) => addSbtPlugin(m)
  case _ => sys.error("Pls set system property '-Dplugin.version=org:name:version' using the scriptedLaunchOpts")
}
