package io.vamp.lifter

import io.vamp.common.util.ObjectUtil
import io.vamp.common.{ Config, ConfigFilter, Namespace }
import org.json4s.{ DefaultFormats, Formats }

object LifterConfiguration {

  val filter = ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") && !k.startsWith("vamp.lifter") })

  def init(implicit namespace: Namespace): Unit = {
    Config.load()
    Config.load(static(namespace))
  }

  def static(implicit namespace: Namespace): Map[String, Any] = {
    implicit val formats: Formats = DefaultFormats
    ObjectUtil.merge(
      Config.export(Config.Type.application, flatten = false, filter),
      Config.export(Config.Type.environment, flatten = false, filter),
      Config.export(Config.Type.system, flatten = false, filter)
    )
  }

  def dynamic(config: Map[String, Any])(implicit namespace: Namespace): Unit = Config.load(config)

  def dynamic(implicit namespace: Namespace): Map[String, Any] = Config.export(Config.Type.dynamic, flatten = false, filter)
}
