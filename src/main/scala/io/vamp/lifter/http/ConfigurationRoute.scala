package io.vamp.lifter.http

import akka.http.scaladsl.model.HttpMethods.{ POST, PUT }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.{ Config, ConfigFilter, Namespace }

import scala.concurrent.Future

trait ConfigurationRoute {
  this: HttpApiDirectives ⇒

  implicit def namespace: Namespace

  private val filter = ConfigFilter({ (k, _) ⇒ k.startsWith("vamp.") && !k.startsWith("vamp.lifter") })

  private var config: Map[String, Any] = Config.export(Config.Type.application, flatten = false, filter)

  lazy val configurationRoutes: Route = {
    pathPrefix("configuration" | "config") {
      get {
        pathEndOrSingleSlash {
          parameters('static.as[Boolean] ? false) { static ⇒
            onSuccess(configuration(static)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          onSuccess(configuration(request)) { result ⇒
            respondWith(Accepted, result)
          }
        }
      }
    }
  }

  private def configuration(static: Boolean = false): Future[Map[String, Any]] = Future.successful {
    if (static) Config.export(Config.Type.application, flatten = false, filter) else config
  }

  private def configuration(input: String): Future[Map[String, Any]] = {
    config = Config.unmarshall(input, filter)
    configuration()
  }
}
