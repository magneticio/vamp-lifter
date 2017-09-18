package io.vamp.lifter.http

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.controller.InfoController

trait InfoRoute extends InfoController {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  lazy val infoRoutes: Route = {
    path("information" | "info") {
      get {
        onSuccess(infoMessage(Set())) {
          case (result, succeeded) ⇒ respondWith(if (succeeded) OK else ServiceUnavailable, result)
        }
      }
    }
  }
}
