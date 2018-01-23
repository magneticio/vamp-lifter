package io.vamp.lifter.persistence

import akka.actor.{ Actor, ActorRef }
import cats.data.Kleisli
import cats.implicits.{ catsStdInstancesForList, toTraverseOps }
import cats.instances.future.catsStdInstancesForFuture
import cats.~>
import io.vamp.common.{ Config, Namespace }
import io.vamp.lifter.notification.{ PersistenceInitializationFailure, PersistenceInitializationSuccess }
import io.vamp.lifter.persistence.LifterPersistenceDSL.{ LiftAction, _ }
import io.vamp.lifter.persistence.SqlDSL._
import io.vamp.lifter.persistence.SqlInterpreter.{ LifterResult, SqlResult }

import scala.io.Source

class SqlPersistenceInitializationActor(val sqlDialectInterpreter: SqlDSL ~> SqlResult, val sqlResource: String) extends PersistenceInitializationActor {

  import PersistenceInitializationActor._

  var initialized = false

  override def receive: Actor.Receive = {
    case Initialize ⇒ if (initialized) {
      log.info(s"Init-Steps for SQL Persistence  already done for namespace ${namespace.name}; Not doing again.")
      sender() ! Initialized
    } else {
      log.info(s"Performing Init-Steps for SQL Persistence ${namespace.name}")
      doInitialization(sender())
      log.info(s"Finished Init-Steps for SQL Persistence ${namespace.name}")
      initialized = true
      sender() ! Initialized
    }
    case _ ⇒ ()
  }

  def doInitialization(senderContext: ActorRef): Unit = {
    val receiver = senderContext
    val vampDatabaseUrl = Config.string("vamp.persistence.database.sql.database-server-url")()

    val (url, db, table) = {
      val url = Config.string("vamp.persistence.database.sql.url")()
      val db = Config.string("vamp.persistence.database.sql.database")()
      val table = Config.string("vamp.persistence.database.sql.table")()

      val urlNs = resolveWithVariables(url, connectionVariables())
      if (urlNs._2.get("namespace").isDefined) (
        urlNs._1,
        resolveWithNamespace(db),
        resolveWithVariables(table, connectionVariables())._1
      )
      else (
        urlNs._1,
        resolveWithVariables(db, connectionVariables())._1, // strict check would be to require no namespace
        resolveWithNamespace(table)
      )
    }

    val user = Config.string("vamp.persistence.database.sql.user")()
    val password = Config.string("vamp.persistence.database.sql.password")()
    val sqlLifterSeed = SqlLifterSeed(db, user, password, url, vampDatabaseUrl)

    val tableQueries: List[String] = Source
      .fromInputStream(getClass.getResourceAsStream(sqlResource))
      .mkString
      .replaceAllLiterally(s"$$table", table)
      .split(';')
      .toList
      .map(_.trim)
      .filterNot(_.isEmpty)

    if (sqlResource == "sqlite.sql") {
      val createSqlLiteTableAndDatabase = for {
        _ ← getConnection(default = true)
        databases ← tableQueries.traverse[SqlAction, Boolean] { tableQuery ⇒
          for {
            connection ← getConnection(default = false)
            statement ← createStatement(connection)
            result ← executeStatement(statement, tableQuery)
            _ ← closeStatement(statement)
            _ ← closeConnection(connection)
          } yield result
        }.map(_.forall(identity))
      } yield databases

      val executeSQLiteActions: Kleisli[LifterResult, SqlLifterSeed, Boolean] =
        createSqlLiteTableAndDatabase.foldMap(sqlDialectInterpreter)

      executeSQLiteActions(sqlLifterSeed).value.foreach {
        case Left(errorMessage) ⇒ reportException(PersistenceInitializationFailure(errorMessage))
        case Right(_) ⇒
          receiver ! Initialized
          info(PersistenceInitializationSuccess)
      }
    } else {

      val sqlInitCommand: LiftAction[Boolean] = for {
        databaseCreated ← createDatabase
        tablesCreated ← createTables(tableQueries)
      } yield databaseCreated && tablesCreated

      val executeSqlActions: Kleisli[LifterResult, SqlLifterSeed, Boolean] =
        sqlInitCommand
          .foldMap[SqlAction](sqlInterpreter)
          .foldMap[SqlResult](sqlDialectInterpreter)

      val result = LifterPersistenceDSL.safeAwait(executeSqlActions(sqlLifterSeed).value, log)
      result match {
        case Left(errorMessage) ⇒ reportException(PersistenceInitializationFailure(errorMessage))
        case Right(_) ⇒
          receiver ! Initialized
          info(PersistenceInitializationSuccess)
      }
    }
  }

  protected def connectionVariables()(implicit namespace: Namespace): Map[String, String] = Map("namespace" → namespace.name)
}
