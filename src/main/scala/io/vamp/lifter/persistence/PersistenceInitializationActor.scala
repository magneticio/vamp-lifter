package io.vamp.lifter.persistence

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.persistence.PersistenceInitializationActor.{ Initialize, Initialized }
import io.vamp.model.resolver.NamespaceValueResolver

object PersistenceInitializationActor {

  object Initialize

  object Initialized

}

class PersistenceInitializationActor extends CommonSupportForActors with NamespaceValueResolver with LifterNotificationProvider {
  override def receive: Receive = {
    case Initialize ⇒ sender() ! Initialized
    case _          ⇒
  }
}

