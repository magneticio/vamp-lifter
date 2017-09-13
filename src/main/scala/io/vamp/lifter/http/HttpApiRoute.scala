package io.vamp.lifter.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.http_api.AbstractHttpApiRoute
import io.vamp.lifter.notification.LifterNotificationProvider

class HttpApiRoute(implicit val actorSystem: ActorSystem, val namespace: Namespace, val materializer: Materializer)
    extends AbstractHttpApiRoute
    with ConfigurationRoute
    with LifterNotificationProvider {

  implicit val timeout: Timeout = Config.timeout("vamp.lifter.http-api.response-timeout")()

  private lazy val index = Config.string("vamp.lifter.http-api.ui.index")()
  private lazy val directory = Config.string("vamp.lifter.http-api.ui.directory")()

  lazy val routes: Route = {
    log {
      handleExceptions(exceptionHandler) {
        handleRejections(rejectionHandler) {
          withRequestTimeout(timeout.duration) {
            apiRoutes ~ uiRoutes
          }
        }
      }
    }
  }

  private lazy val apiRoutes: Route = {
    noCachingAllowed {
      cors() {
        pathPrefix("api") {
          encodeResponse {
            decodeRequest {
              configurationRoutes
            }
          }
        }
      }
    }
  }

  private lazy val uiRoutes = path("") {
    encodeResponse {
      if (index.isEmpty) notFound else getFromFile(index)
    }
  } ~ pathPrefix("") {
    encodeResponse {
      if (directory.isEmpty) notFound else getFromDirectory(directory)
    }
  }

  private def notFound = respondWith(NotFound, HttpEntity("The requested resource could not be found."))
}
