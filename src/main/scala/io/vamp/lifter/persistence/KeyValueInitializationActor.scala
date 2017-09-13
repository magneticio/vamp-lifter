package io.vamp.lifter.persistence

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.common.{ Config, ConfigFilter }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.notification.InvalidConfigurationError
import io.vamp.persistence.{ KeyValueStoreActor, PersistenceActor }

class KeyValueInitializationActor extends CommonSupportForActors with LifterNotificationProvider {

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  def receive: Actor.Receive = {
    case "init" ⇒ store()
    case _      ⇒
  }

  override def preStart(): Unit = self ! "init"

  private def store() = try {
    val config = Config.export(
      Config.Type.application,
      flatten = false,
      ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") && !k.startsWith("vamp.lifter") })
    )

    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set("configuration" :: Nil, if (config.isEmpty) None else Option(Config.marshall(config))) map { _ ⇒
      log.info("Configuration has been persisted to key-value store")
    } recover {
      case _ ⇒ throwException(InvalidConfigurationError)
    }
  } catch {
    case _: Exception ⇒ throwException(InvalidConfigurationError)
  }
}