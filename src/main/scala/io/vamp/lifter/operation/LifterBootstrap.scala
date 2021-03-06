package io.vamp.lifter.operation

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.vitals.InfoRequest
import io.vamp.common.{ Config, Namespace }
import io.vamp.lifter.artifact.ArtifactInitializationActor
import io.vamp.lifter.persistence.SqlInterpreter.SqlInterpreter
import io.vamp.lifter.persistence.{ PersistenceInitializationActor, SqlInterpreter, SqlPersistenceInitializationActor }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.{ ExecutionContext, Future }

class LifterBootstrap(implicit override val actorSystem: ActorSystem, val namespace: Namespace, override val timeout: Timeout)
    extends ActorBootstrap with VampInitialization {

  implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    val initialize = Config.boolean("vamp.lifter.auto-initialize")()
    val db = Config.string("vamp.persistence.database.type")().toLowerCase

    logger.info(s"database: $db")

    val dbActor = db match {
      case "mysql"     ⇒ sqlPersistence(SqlInterpreter.mysqlInterpreter, "mysql.sql")
      case "postgres"  ⇒ sqlPersistence(SqlInterpreter.postgresqlInterpreter, "postgres.sql")
      case "sqlserver" ⇒ sqlPersistence(SqlInterpreter.sqlServerInterpreter, "sqlserver.sql")
      case "sqlite"    ⇒ sqlPersistence(SqlInterpreter.sqLiteInterpreter, "sqlite.sql")
      case _           ⇒ sqlPersistence()
    }

    Future.sequence(
      IoC.createActor[ArtifactInitializationActor] :: dbActor
    ).map { list ⇒
        if (initialize) execute()
        list
      }
  }

  private def execute(): Unit = actorSystem.scheduler.scheduleOnce(
    Config.duration("vamp.lifter.bootstrap.delay")(),
    () ⇒ {
      val (drivers, artifacts) = template().partition(_._1 != "artifacts")
      setup(drivers).foreach { _ ⇒
        IoC.actorFor[PersistenceActor] ? InfoRequest map {
          case m: Map[_, _] ⇒
            val db = m.asInstanceOf[Map[String, Map[String, Any]]].getOrElse("database", Map[String, Any]())
            db.getOrElse("records", 0) match {
              case i: Int if i == 0 ⇒ setup(artifacts)
              case _                ⇒ logger.info("Skipping artifact creation because DB contains already some records.")
            }
          case _ ⇒ logger.error("Cannot retrieve DB record count!")
        }
      }
    }: Unit
  )(actorSystem.dispatcher)

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[Unit] = Future.successful(())

  private def sqlPersistence(): List[Future[ActorRef]] = List(IoC.createActor[PersistenceInitializationActor])

  private def sqlPersistence(interpreter: SqlInterpreter, resource: String): List[Future[ActorRef]] = {
    IoC.alias[PersistenceInitializationActor, SqlPersistenceInitializationActor]
    List(IoC.createActor[SqlPersistenceInitializationActor](interpreter, resource))
  }
}

trait VampInitialization {

  implicit def timeout: Timeout

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  protected def template()(implicit namespace: Namespace): Map[String, Any] = Map(
    "key_value" → true,
    "persistence" → true,
    "artifacts" → Config.stringList("vamp.lifter.artifacts")().map(artifact ⇒ artifact → true).toMap
  )

  protected def setup(mapping: Map[String, Any])(implicit namespace: Namespace): Future[Any] = for {
    _ ← setupKvStore(mapping)
    _ ← setupPersistence(mapping)
    _ ← setupArtifacts(mapping)
  } yield mapping

  protected def setupKvStore(mapping: Map[String, Any])(implicit namespace: Namespace): Future[Any] = {
    if (mapping.getOrElse("key_value", false).asInstanceOf[Boolean])
      IoC.actorFor[ConfigActor] ? ConfigActor.Push(namespace.name)
    else Future.successful(true)
  }

  protected def setupPersistence(mapping: Map[String, Any])(implicit namespace: Namespace): Future[Any] = {
    if (mapping.getOrElse("persistence", false).asInstanceOf[Boolean])
      IoC.actorFor[PersistenceInitializationActor] ? PersistenceInitializationActor.Initialize
    else Future.successful(true)
  }

  protected def setupArtifacts(mapping: Map[String, Any])(implicit namespace: Namespace): Future[Any] = {
    val files = mapping.getOrElse("artifacts", Map()).asInstanceOf[Map[String, Boolean]].collect {
      case (file, true) ⇒ file
    }.toList
    IoC.actorFor[ArtifactInitializationActor] ? ArtifactInitializationActor.Load(files)
  }
}