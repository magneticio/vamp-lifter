package io.vamp.lifter.persistence

import akka.actor.{ Actor, ActorRef }
import cats.data.Kleisli
import cats.implicits.{ catsStdInstancesForList, toTraverseOps }
import cats.instances.future.catsStdInstancesForFuture
import cats.~>
import io.vamp.common.Config
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
      doInitialization(sender());
      log.info(s"Finished Init-Steps for SQL Persistence ${namespace.name}")
      initialized = true
      sender() ! Initialized
    }
    case _ ⇒ ()
  }

  def doInitialization(senderContext: ActorRef): Unit = {
    val receiver = senderContext
    val url = resolveWithNamespace(Config.string("vamp.persistence.database.sql.url")())
    val vampDatabaseUrl = Config.string("vamp.persistence.database.sql.database-server-url")()
    val db = resolveWithNamespace(Config.string("vamp.persistence.database.sql.database")())
    val table = Config.string("vamp.persistence.database.sql.table")()
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

      executeSqlActions(sqlLifterSeed).value.foreach {
        case Left(errorMessage) ⇒ reportException(PersistenceInitializationFailure(errorMessage))
        case Right(_) ⇒
          receiver ! Initialized
          info(PersistenceInitializationSuccess)
      }
    }
  }
}
