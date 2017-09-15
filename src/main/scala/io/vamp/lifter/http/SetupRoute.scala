package io.vamp.lifter.http

import akka.http.scaladsl.model.HttpMethods.{ POST, PUT }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import io.vamp.common.http.HttpApiDirectives
import io.vamp.lifter.VampInitialization
import org.json4s.DefaultFormats
import org.json4s.native.Serialization

trait SetupRoute extends VampInitialization {
  this: HttpApiDirectives ⇒

  lazy val setupRoutes: Route = {
    path("setup") {
      get {
        respondWith(OK, template())
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          onSuccess(setup(read(request))) { result ⇒
            respondWith(OK, result)
          }
        }
      }
    }
  }

  private def read(request: String): Map[String, Any] = {
    implicit val formats: DefaultFormats = DefaultFormats
    Serialization.read[Any](request).asInstanceOf[Map[String, Any]]
  }
}
