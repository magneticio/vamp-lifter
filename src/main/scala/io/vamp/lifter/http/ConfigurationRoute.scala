package io.vamp.lifter.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods.{ POST, PUT }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC
import io.vamp.common.http.HttpApiDirectives
import io.vamp.common.{ Config, Namespace }
import io.vamp.lifter.LifterConfiguration
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.notification.InvalidConfigurationError
import io.vamp.persistence.KeyValueStoreActor

import scala.concurrent.{ ExecutionContext, Future }

trait ConfigurationRoute {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  import LifterConfiguration.filter

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  lazy val configurationRoutes: Route = {
    pathPrefix("configuration" | "config") {
      get {
        pathEndOrSingleSlash {
          parameters('static.as[Boolean] ? false) { static ⇒
            parameters('key_value.as[Boolean] ? false) { kv ⇒
              onSuccess(configuration(static, kv)) { result ⇒
                respondWith(OK, result)
              }
            }
          }
        }
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          parameters('key_value.as[Boolean] ? false) { kv ⇒
            onSuccess(configuration(request, kv)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    }
  }

  private def configuration(static: Boolean, kv: Boolean): Future[Map[String, Any]] = {
    if (static) Future.successful(LifterConfiguration.static) else if (kv) {
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get("configuration" :: Nil) map {
        case Some(content: String) ⇒ Config.unmarshall(content)
        case _                     ⇒ Map[String, Any]()
      }
    } else Future.successful(LifterConfiguration.dynamic)
  }

  private def configuration(input: String, kv: Boolean): Future[Map[String, Any]] = try {
    val cfg = if (input.trim.isEmpty) Map[String, Any]() else Config.unmarshall(input.trim, filter)

    if (kv) IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set("configuration" :: Nil, if (cfg.isEmpty) None else Option(Config.marshall(cfg))) map { _ ⇒
      LifterConfiguration.dynamic(cfg)
    }
    else Future.successful(LifterConfiguration.dynamic(cfg))
  } catch {
    case _: Exception ⇒ throwException(InvalidConfigurationError)
  }
}
