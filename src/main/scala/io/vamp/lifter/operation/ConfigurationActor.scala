package io.vamp.lifter.operation

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.common.util.ObjectUtil
import io.vamp.common.{ Config, ConfigFilter, Namespace }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.persistence.{ KeyValueStoreActor, PersistenceActor }
import org.json4s.{ DefaultFormats, Formats }

object ConfigurationActor {

  val filter = ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") && !k.startsWith("vamp.lifter") })

  case class Init(namespace: Namespace)

  case class Get(namespace: String, static: Boolean, dynamic: Boolean, kv: Boolean)

  case class Set(namespace: String, config: Map[String, Any])

  case class Push(namespace: String)

}

class ConfigurationActor extends CommonSupportForActors with LifterNotificationProvider {

  import ConfigurationActor._

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  def receive: Actor.Receive = {
    case i: Init ⇒ init(i.namespace)
    case g: Get  ⇒ get(g.namespace, g.static, g.dynamic, g.kv)
    case s: Set  ⇒ set(s.namespace, s.config)
    case p: Push ⇒ push(p.namespace)
    case _       ⇒
  }

  private def init(implicit namespace: Namespace): Unit = {
    Config.load()
    Config.load(static)
    sender() ! true
  }

  private def get(namespace: String, static: Boolean, dynamic: Boolean, kv: Boolean): Unit = {
    val receiver = sender()
    implicit val ns: Namespace = Namespace(namespace)
    if (static)
      receiver ! this.static
    else if (dynamic)
      receiver ! Config.export(Config.Type.dynamic, flatten = false, filter)
    else if (kv) {
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get("configuration" :: Nil) map {
        case Some(content: String) ⇒ receiver ! Config.unmarshall(content)
        case _                     ⇒ receiver ! Map[String, Any]()
      }
    }
  }

  private def set(namespace: String, config: Map[String, Any]): Unit = {
    val receiver = sender()
    implicit val ns: Namespace = Namespace(namespace)
    Config.load(config)
    receiver ! true
  }

  private def push(namespace: String): Unit = {
    val receiver = sender()
    implicit val ns: Namespace = Namespace(namespace)
    val config = Config.export(Config.Type.dynamic, flatten = false, filter)
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set("configuration" :: Nil, if (config.isEmpty) None else Option(Config.marshall(config))) foreach { _ ⇒
      receiver ! true
    }
  }

  private def static(implicit namespace: Namespace): Map[String, Any] = {
    implicit val formats: Formats = DefaultFormats
    ObjectUtil.merge(
      Config.export(Config.Type.application, flatten = false, filter),
      Config.export(Config.Type.environment, flatten = false, filter),
      Config.export(Config.Type.system, flatten = false, filter)
    )
  }
}
