package io.vamp.lifter.http

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.controller.InfoController

trait ConnectionRoute extends InfoController {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  lazy val connectionRoutes: Route = {
    path("connections" | "connection") {
      get {
        onSuccess(infoMessage(List("persistence", "key_value", "pulse", "container_driver").toSet)) {
          case (result, _) ⇒ respondWith(OK, result)
        }
      }
    }
  }
}
