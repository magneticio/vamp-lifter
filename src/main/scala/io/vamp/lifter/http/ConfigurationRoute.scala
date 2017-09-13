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
          onSuccess(configuration()) { result ⇒
            respondWith(OK, result)
          }
        }
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          onSuccess(configuration(Option(request))) { result ⇒
            respondWith(Accepted, result)
          }
        }
      }
    }
  }

  private def configuration(input: Option[String] = None): Future[Map[String, Any]] = {
    input.foreach(c ⇒ config = Config.unmarshall(c, filter))
    Future.successful(config)
  }
}
