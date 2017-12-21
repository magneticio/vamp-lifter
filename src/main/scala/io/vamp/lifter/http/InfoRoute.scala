package io.vamp.lifter.http

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpApiDirectives
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.controller.utilcontroller.InfoController

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
}
