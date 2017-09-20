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
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.operation.ConfigurationActor
import io.vamp.lifter.operation.ConfigurationActor.Get
import io.vamp.operation.notification.InvalidConfigurationError

import scala.concurrent.{ ExecutionContext, Future }

trait ConfigurationRoute {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  implicit def timeout: Timeout

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  def configurationRoutes(implicit namespace: Namespace): Route = {
    path("configuration" | "config") {
      get {
        parameters('static.as[Boolean] ? false) { static ⇒
          parameters('key_value.as[Boolean] ? false) { kv ⇒
            onSuccess(configuration(static, kv)) { result ⇒
              respondWith(OK, result)
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

  private def configuration(static: Boolean, kv: Boolean)(implicit namespace: Namespace): Future[Map[String, Any]] = {
    val actor = IoC.actorFor[ConfigurationActor]
    val request = Get(namespace.name, static = false, dynamic = false, kv = false)
    (
      if (static) actor ? request.copy(static = true)
      else if (kv) actor ? request.copy(kv = true)
      else actor ? request.copy(dynamic = true)
    ) map (_.asInstanceOf[Map[String, Any]])
  }

  private def configuration(input: String, kv: Boolean)(implicit namespace: Namespace): Future[Map[String, Any]] = try {
    val cfg = if (input.trim.isEmpty) Map[String, Any]() else Config.unmarshall(input.trim, ConfigurationActor.filter)

    IoC.actorFor[ConfigurationActor] ? ConfigurationActor.Set(namespace.name, cfg) flatMap { _ ⇒
      if (kv) IoC.actorFor[ConfigurationActor] ? ConfigurationActor.Push(namespace.name) map { _ ⇒
        actorSystem.actorSelection(s"/user/${namespace.name}-config") ! "reload"
        cfg
      }
      else Future.successful(cfg)
    }
  } catch {
    case _: Exception ⇒ throwException(InvalidConfigurationError)
  }
}