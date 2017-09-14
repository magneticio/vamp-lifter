package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.{ SqlInterpreter, SqlPersistenceInitializationActor }
import io.vamp.lifter.pulse.PulseInitializationActor

import scala.concurrent.{ ExecutionContext, Future }

class SetupBootstrap extends ActorBootstrap {

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

    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    Future.sequence(IoC.createActor[PulseInitializationActor] :: IoC.createActor[ArtifactInitializationActor] :: dbActor)
  }
}
