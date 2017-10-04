package io.vamp.lifter.operation

import akka.actor.{ Actor, ActorRef }
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.common.util.ObjectUtil
import io.vamp.common.{ Config, ConfigFilter, Namespace }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.config.ConfigurationLoaderActor
import io.vamp.persistence.{ KeyValueStoreActor, PersistenceActor }
import org.json4s.{ DefaultFormats, Formats }

object ConfigActor {

  val path: List[String] = ConfigurationLoaderActor.path

  val filterVamp = ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") })

  val filterVampNoLifter = ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") && !k.startsWith("vamp.lifter.") })

  case class Init(namespace: Namespace)

  case class Load(namespace: Namespace)

  case class Get(namespace: String, static: Boolean, dynamic: Boolean, kv: Boolean)

  case class Set(namespace: String, config: String)

  case class Push(namespace: String)

}

case class ConfigActorArgs(
  filter:            ConfigFilter = ConfigActor.filterVampNoLifter,
  supportStatic:     Boolean      = true,
  overrideNamespace: Boolean      = false,
  pathWithNamespace: Boolean      = false
)

class ConfigActor(args: ConfigActorArgs) extends CommonSupportForActors with LifterNotificationProvider {

  import ConfigActor._

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  def receive: Actor.Receive = {
    case i: Init ⇒ init(i.namespace)
    case l: Load ⇒ load(l.namespace)
    case g: Get  ⇒ get(g.namespace, g.static, g.dynamic, g.kv)
    case s: Set  ⇒ set(s.namespace, s.config)
    case p: Push ⇒ push(p.namespace)
    case _       ⇒
  }

  protected def init(implicit namespace: Namespace): Unit = {
    Config.load()
    if (supportStatic(namespace.name)) Config.load(static)
    sender() ! true
  }

  protected def load(implicit namespace: Namespace): Unit = {
    val receiver = sender()
    keyValueActor(namespace) ? KeyValueStoreActor.Get(path(namespace.name)) map {
      case Some(content: String) ⇒ Config.load(Config.unmarshall(content))
      case _                     ⇒
    } foreach { _ ⇒ receiver ! true }
  }

  protected def get(namespace: String, static: Boolean, dynamic: Boolean, kv: Boolean): Unit = {
    val receiver = sender()
    implicit val ns: Namespace = Namespace(namespace)
    if (static && supportStatic(namespace))
      receiver ! this.static
    else if (dynamic)
      receiver ! Config.export(Config.Type.dynamic, flatten = false, args.filter)
    else if (kv) {
      keyValueActor(ns) ? KeyValueStoreActor.Get(path(namespace)) map {
        case Some(content: String) ⇒ receiver ! Config.unmarshall(content)
        case _                     ⇒ receiver ! Map[String, Any]()
      }
    } else receiver ! Map[String, Any]()
  }

  protected def set(namespace: String, input: String): Unit = {
    val receiver = sender()

    implicit val ns: Namespace = Namespace(namespace)
    var config = if (input.trim.isEmpty) Map[String, Any]() else Config.unmarshall(input.trim, args.filter)

    if (args.overrideNamespace) {
      implicit val formats: Formats = DefaultFormats
      config = ObjectUtil.merge(Map("vamp" → Map("namespace" → namespace)), config)
    }

    Config.load(config)
    receiver ! config
  }

  protected def push(namespace: String): Unit = {
    val receiver = sender()
    implicit val ns: Namespace = Namespace(namespace)
    val config = Config.export(Config.Type.dynamic, flatten = false, args.filter)
    keyValueActor(ns) ? KeyValueStoreActor.Set(path(namespace), if (config.isEmpty) None else Option(Config.marshall(config))) foreach { _ ⇒
      receiver ! true
    }
  }

  protected def static(implicit namespace: Namespace): Map[String, Any] = {
    implicit val formats: Formats = DefaultFormats
    ObjectUtil.merge(
      Config.export(Config.Type.application, flatten = false, args.filter),
      Config.export(Config.Type.environment, flatten = false, args.filter),
      Config.export(Config.Type.system, flatten = false, args.filter)
    )
  }

  protected def supportStatic(namespace: String): Boolean = args.supportStatic || this.namespace.name == namespace

  protected def keyValueActor(namespace: Namespace): ActorRef = IoC.actorFor(classOf[KeyValueStoreActor])(actorSystem, namespace)

  protected def path(namespace: String): List[String] = if (args.pathWithNamespace) namespace :: ConfigActor.path else ConfigActor.path
}
