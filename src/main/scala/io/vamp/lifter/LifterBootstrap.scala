package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.{ Config, Namespace }
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.{ SqlInterpreter, SqlPersistenceInitializationActor }
import io.vamp.lifter.pulse.PulseInitializationActor
import io.vamp.persistence.KeyValueStoreActor

import scala.concurrent.{ ExecutionContext, Future }

class LifterBootstrap(initialize: Boolean)(implicit override val actorSystem: ActorSystem, override val namespace: Namespace, override val timeout: Timeout)
    extends ActorBootstrap with VampInitialization {

  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val db = Config.string("vamp.persistence.database.type")().toLowerCase

    logger.info(s"database: $db")

    val dbActor = db match {
      case "mysql"     ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.mysqlInterpreter, "mysql.sql"))
      case "postgres"  ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.postgresqlInterpreter, "postgres.sql"))
      case "sqlserver" ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.sqlServerInterpreter, "sqlserver.sql"))
      case "sqlite"    ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.sqLiteInterpreter, "sqlite.sql"))
      case _           ⇒ Nil
    }

    Future.sequence(IoC.createActor[PulseInitializationActor] :: IoC.createActor[ArtifactInitializationActor] :: dbActor).flatMap {
      list ⇒ if (initialize) setup(template()).map(_ ⇒ list) else Future.successful(list)
    }
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {}
}

trait VampInitialization {

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  protected def template(): Map[String, Any] = Map(
    "key_value" → true,
    "persistence" → true,
    "pulse" → true,
    "artifacts" → Config.stringList("vamp.lifter.artifacts")().map(artifact ⇒ artifact → true).toMap
  )

  protected def setup(mapping: Map[String, Any]): Future[Any] = for {
    _ ← setupKvStore(mapping)
    _ ← setupPersistence(mapping)
    _ ← setupPulse(mapping)
    _ ← setupArtifacts(mapping)
  } yield mapping

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