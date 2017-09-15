package io.vamp.lifter.pulse

import akka.actor.{ Actor, ActorRef }
import akka.util.Timeout
import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.pulse.PulseInitializationActor.Initialize
import io.vamp.pulse.PulseActor

object PulseInitializationActor {

  object Initialize

}

class PulseInitializationActor extends ElasticsearchPulseInitializationActor with CommonSupportForActors with LifterNotificationProvider {

  implicit lazy val timeout: Timeout = PulseActor.timeout()

  def receive: Actor.Receive = {
    case Initialize ⇒ initialize()
    case _          ⇒ done(sender())
  }

  private def initialize(): Unit = {
    val receiver = sender()
    val pulse = Config.string("vamp.pulse.type")().toLowerCase
    log.info(s"Pulse type: $pulse")

    pulse match {
      case "elasticsearch" ⇒ initializeElasticsearch().foreach(_ ⇒ done(receiver))
      case _               ⇒ done(receiver)
    }
  }

  private def done(receiver: ActorRef): Unit = {
    log.info(s"Pulse has been initialized.")
    receiver ! true
  }
}
