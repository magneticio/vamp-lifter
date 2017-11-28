package io.vamp.lifter.persistence

import akka.event.LoggingAdapter
import cats.free._
import cats.~>
import io.vamp.lifter.persistence.SqlDSL._
import cats.implicits.catsStdInstancesForList
import cats.implicits.toTraverseOps

import scala.concurrent.{ Await, Future, TimeoutException }
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._
/**
 * Describes the lifting action of persistence in pure data
 */
sealed trait LifterPersistenceDSL[A]

case object CreateDatabase extends LifterPersistenceDSL[Boolean]

/**
 * Creates the instruction for creating tables in each underlying SQL implementation
 * @param tableQueries are read from a sql file in persistence
 */
case class CreateTables(tableQueries: List[String]) extends LifterPersistenceDSL[Boolean]

object LifterPersistenceDSL {

  def safeAwait[T](action: ⇒ Future[T], logger: LoggingAdapter): T = {
    Try(Await.result(action, 10.seconds)) match {
      case Success(s) ⇒ s
      case Failure(f: TimeoutException) ⇒ {
        logger.error(s"Timeout after 10 seconds at initialization; Stack Trace: ${f.getStackTrace}")
        throw f
      }
      case Failure(x) ⇒ throw x
    }
  }

  type LiftAction[A] = Free[LifterPersistenceDSL, A]

  def createDatabase: LiftAction[Boolean] =
    Free.liftF(CreateDatabase)

  def createTables(tableQueries: List[String]): LiftAction[Boolean] =
    Free.liftF(CreateTables(tableQueries))

  implicit val sqlInterpreter: LifterPersistenceDSL ~> SqlAction = new (LifterPersistenceDSL ~> SqlAction) {
    def apply[A](lifterPersistenceDSL: LifterPersistenceDSL[A]): SqlAction[A] = lifterPersistenceDSL match {
      case CreateDatabase ⇒
        for {
          connection ← getConnection(default = true)
          statement ← createStatement(connection)
          query ← selectDatabasesQuery
          dbCreateQuery ← createDatabaseQuery
          result ← executeQuery(statement, query)
          dbCreated ← createDatabaseIfNotExistsIn(result, dbCreateQuery)
          _ ← closeStatement(statement)
          _ ← closeConnection(connection)
        } yield dbCreated
      case CreateTables(tableQueries) ⇒
        tableQueries.traverse[SqlAction, Boolean] { tableQuery ⇒
          for {
            connection ← getConnection(default = false)
            statement ← createStatement(connection)
            result ← executeStatement(statement, tableQuery)
            _ ← closeStatement(statement)
            _ ← closeConnection(connection)
          } yield result
        }.map(_.forall(identity))
    }
  }

}
