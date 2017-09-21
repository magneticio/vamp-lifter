package io.vamp.lifter.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods.{ POST, PUT }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC
import io.vamp.common.http.HttpApiDirectives
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.operation.ConfigurationActor
import io.vamp.lifter.operation.ConfigurationActor.Get
import io.vamp.operation.notification.InvalidConfigurationError

import scala.concurrent.{ ExecutionContext, Future }

trait ConfigurationRoute {
  this: LifterNotificationProvider with HttpApiDirectives ⇒

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  def configurationRoutes(name: String = namespace.name): Route = {
    path("configuration" | "config") {
      get {
        parameters('static.as[Boolean] ? false) { static ⇒
          parameters('key_value.as[Boolean] ? false) { kv ⇒
            onSuccess(configuration(name, static, kv)) { result ⇒
              respondWith(OK, result)
            }
          }
        }
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          parameters('key_value.as[Boolean] ? false) { kv ⇒
            onSuccess(configuration(name, request, kv)) { result ⇒
              respondWith(Accepted, result)
            }
          }
        }
      }
    }
  }

  private def configuration(name: String, static: Boolean, kv: Boolean): Future[Map[String, Any]] = {
    val actor = IoC.actorFor[ConfigurationActor]
    val request = Get(name, static = false, dynamic = false, kv = false)
    (
      if (static) actor ? request.copy(static = true)
      else if (kv) actor ? request.copy(kv = true)
      else actor ? request.copy(dynamic = true)
    ) map (_.asInstanceOf[Map[String, Any]])
  }

  private def configuration(name: String, input: String, kv: Boolean): Future[Any] = try {
    IoC.actorFor[ConfigurationActor] ? ConfigurationActor.Set(name, input) flatMap { config ⇒
      if (kv) IoC.actorFor[ConfigurationActor] ? ConfigurationActor.Push(name) map { _ ⇒
        actorSystem.actorSelection(s"/user/$name-config") ! "reload"
        config
      }
      else Future.successful(config)
    }
  } catch {
    case _: Exception ⇒ throwException(InvalidConfigurationError)
  }
}
