package io.vamp.lifter.pulse

import akka.actor.{ Actor, ActorRef, Terminated }
import akka.util.Timeout
import io.vamp.common.Config
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.pulse.PulseInitializationActor.Initialize
import io.vamp.pulse.PulseActor

object PulseInitializationActor {

  object Initialize

}

class PulseInitializationActor extends CommonSupportForActors with LifterNotificationProvider {

  implicit lazy val timeout: Timeout = PulseActor.timeout()

  private var receiver: Option[ActorRef] = None

  def receive: Actor.Receive = {
    case Initialize    ⇒ initialize()
    case Terminated(_) ⇒ done()
    case _             ⇒
  }

  private def initialize(): Unit = {
    receiver = Option(sender())
    val pulse = Config.string("vamp.pulse.type")().toLowerCase
    log.info(s"Pulse type: $pulse")
    pulse match {
      case "elasticsearch" ⇒ IoC.createActor[ElasticsearchPulseInitializationActor].map {
        child ⇒ context.watch(child)
      }
      case _ ⇒ done()
    }
  }

  private def done(): Unit = {
    log.info(s"Pulse has been initialized.")
    receiver.foreach(_ ! true)
  }
}