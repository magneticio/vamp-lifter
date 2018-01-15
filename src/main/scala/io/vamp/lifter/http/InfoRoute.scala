package io.vamp.lifter.http

import akka.actor.Actor
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.controller.utilcontroller.InfoController
import io.vamp.persistence.KeyValueStoreActor
import io.vamp.pulse.PulseActor

trait InfoRoute extends InfoController {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  implicit def timeout: Timeout

  def infoRoutes(on: Set[String] = Set())(implicit namespace: Namespace): Route = {
    path("information" | "info") {
      get {
        onSuccess(infoMessage(on)) {
          case (result, succeeded) ⇒ respondWith(if (succeeded) OK else ServiceUnavailable, result)
        }
      }
    }
  }

  override protected def infoActors(on: Set[String]): List[Class[Actor]] = {
    val list = if (on.isEmpty) {
      List(
        classOf[KeyValueStoreActor],
        classOf[PulseActor],
        classOf[ContainerDriverActor]
      )
    } else on.map(_.toLowerCase).collect {
      case "key_value"        ⇒ classOf[KeyValueStoreActor]
      case "pulse"            ⇒ classOf[PulseActor]
      case "container_driver" ⇒ classOf[ContainerDriverActor]
    } toList

    list.map(_.asInstanceOf[Class[Actor]])
  }
}
