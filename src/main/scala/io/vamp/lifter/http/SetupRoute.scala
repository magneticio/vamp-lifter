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
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.SqlPersistenceInitializationActor
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.persistence.KeyValueStoreActor
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

import scala.concurrent.{ ExecutionContext, Future }

trait SetupRoute {
  this: HttpApiDirectives ⇒

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  lazy val setupRoutes: Route = {
    path("setup") {
      get {
        respondWith(OK, template())
      } ~ (method(PUT) | method(POST)) {
        entity(as[String]) { request ⇒
          onSuccess(setup(request)) { result ⇒
            respondWith(OK, result)
          }
        }
      }
    }
  }

  private def template(): Map[String, Any] = {
    Map(
      "key_value" → true,
      "persistence" → true,
      "pulse" → true,
      "artifacts" → Config.stringList("vamp.lifter.artifacts")().map(artifact ⇒ artifact → true).toMap
    )
  }

  private def setup(request: String): Future[Any] = {
    implicit val formats: DefaultFormats = DefaultFormats
    val mapping = read[Any](request).asInstanceOf[Map[String, Any]]

    for {
      _ ← setupKvStore(mapping)
      _ ← setupPersistence(mapping)
      _ ← setupPulse(mapping)
      _ ← setupArtifacts(mapping)
    } yield mapping
  }

  private def setupKvStore(mapping: Map[String, Any]): Future[Any] = {
    if (mapping.getOrElse("key_value", false).asInstanceOf[Boolean])
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set("configuration" :: Nil, Option(Config.marshall(LifterConfiguration.dynamic)))
    else Future.successful(true)
  }

  private def setupPersistence(mapping: Map[String, Any]): Future[Any] = {
    if (mapping.getOrElse("persistence", false).asInstanceOf[Boolean])
      IoC.actorFor[SqlPersistenceInitializationActor] ? SqlPersistenceInitializationActor.Initialize
    else Future.successful(true)
  }

  private def setupPulse(mapping: Map[String, Any]): Future[Any] = {
    if (mapping.getOrElse("pulse", false).asInstanceOf[Boolean])
      IoC.actorFor[PulseInitializationActor] ? PulseInitializationActor.Initialize
    else Future.successful(true)
  }

  private def setupArtifacts(mapping: Map[String, Any]): Future[Any] = {
    val files = mapping.getOrElse("artifacts", Map()).asInstanceOf[Map[String, Boolean]].collect {
      case (file, true) ⇒ file
    }.toList
    IoC.actorFor[ArtifactInitializationActor] ? ArtifactInitializationActor.Load(files)
  }
}
