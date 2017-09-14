package io.vamp.lifter

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.{ Config, Namespace }
import io.vamp.lifter.persistence._

import scala.concurrent.{ ExecutionContext, Future }

class LifterBootstrap extends ActorBootstrap {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    Future.sequence(createPersistenceActors)
  }

  protected def createPersistenceActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): List[Future[ActorRef]] = {
    val db = Config.string("vamp.persistence.database.type")().toLowerCase

    logger.info(s"database: $db")

    db match {
      case "mysql"     ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.mysqlInterpreter, "mysql.sql"))
      case "postgres"  ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.postgresqlInterpreter, "postgres.sql"))
      case "sqlserver" ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.sqlServerInterpreter, "sqlserver.sql"))
      case "sqlite"    ⇒ List(IoC.createActor[SqlPersistenceInitializationActor](SqlInterpreter.sqLiteInterpreter, "sqlite.sql"))
      case _           ⇒ Nil
    }
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {}
}
