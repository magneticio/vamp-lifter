package io.vamp.lifter.pulse

import akka.actor.{ FSM, _ }
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.NotificationProvider
import io.vamp.lifter.notification.ElasticsearchInitializationTimeoutError
import io.vamp.pulse.{ ElasticsearchClient, PulseActor }

import scala.util.{ Failure, Success }

object ElasticsearchInitializationActor {

  sealed trait InitializationEvent

  object WaitForOne extends InitializationEvent

  object DoneWithOne extends InitializationEvent

  sealed trait State

  case object Phase1 extends State

  case object Phase2 extends State

  case object Phase3 extends State

  case object Done extends State

  case class TemplateDefinition(name: String, template: String)

  case class DocumentDefinition(index: String, `type`: String, id: String, document: String)

}

trait ElasticsearchInitializationActor extends FSM[ElasticsearchInitializationActor.State, Int] with CommonSupportForActors with NotificationProvider {

  import ElasticsearchInitializationActor._

  override val log = akka.event.Logging(this)

  def templates: List[TemplateDefinition] = Nil

  def documents: List[DocumentDefinition] = Nil

  def elasticsearchUrl: String

  lazy implicit val timeout: akka.util.Timeout = PulseActor.timeout()

  private val httpClient = new HttpClient

  def done(): State = stop()

  startWith(Phase1, 1)

  initializeTemplates()

  onTransition {
    case (Phase1, Phase2) ⇒ initializeDocuments()
  }

  onTransition {
    case (Phase2, Phase3) ⇒ initializeCustom()
  }

  when(Phase1, stateTimeout = timeout.duration) {
    case Event(WaitForOne, count)  ⇒ waitForOne(count)
    case Event(DoneWithOne, count) ⇒ doneWithOne(count, () ⇒ goto(Phase2) using 1)
    case Event(StateTimeout, _)    ⇒ expired()
  }

  when(Phase2, stateTimeout = timeout.duration) {
    case Event(WaitForOne, count)  ⇒ waitForOne(count)
    case Event(DoneWithOne, count) ⇒ doneWithOne(count, () ⇒ goto(Phase3) using 1)
    case Event(StateTimeout, _)    ⇒ expired()
  }

  when(Phase3, stateTimeout = timeout.duration) {
    case Event(WaitForOne, count)  ⇒ waitForOne(count)
    case Event(DoneWithOne, count) ⇒ doneWithOne(count, () ⇒ done())
    case Event(StateTimeout, _)    ⇒ expired()
  }

  when(Done) {
    case _ ⇒ stop()
  }

  initialize()

  private def waitForOne(count: Int) = stay() using count + 1

  private def doneWithOne(count: Int, next: () ⇒ State) = if (count > 1) stay() using count - 1 else next()

  private def expired() = {
    reportException(ElasticsearchInitializationTimeoutError)
    done()
  }

  protected def initializeTemplates(): Unit = {
    val receiver = self

    def createTemplate(definition: TemplateDefinition): Unit = {
      receiver ! WaitForOne
      httpClient.put[Any](s"$elasticsearchUrl/_template/${definition.name}", definition.template) onComplete {
        _ ⇒ receiver ! DoneWithOne
      }
    }

    httpClient.get[Any](s"$elasticsearchUrl/_template") onComplete {
      case Success(response) ⇒
        response match {
          case map: Map[_, _] ⇒ templates.filterNot(definition ⇒ map.asInstanceOf[Map[String, Any]].contains(definition.name)).foreach(createTemplate)
          case _              ⇒ templates.foreach(createTemplate)
        }
        receiver ! DoneWithOne

      case Failure(t) ⇒
        log.warning(s"Failed Elasticsearch initialization: $t")
        receiver ! DoneWithOne
    }
  }

  protected def initializeDocuments(): Unit = {
    val receiver = self
    val es = new ElasticsearchClient(elasticsearchUrl)

    documents.foreach { definition ⇒
      es.exists(definition.index, definition.`type`, definition.id).map {
        case true ⇒
        case false ⇒
          receiver ! WaitForOne
          es.index[Any](definition.index, definition.`type`, definition.id, definition.document) onComplete {
            _ ⇒ receiver ! DoneWithOne
          }
      }
    }

    receiver ! DoneWithOne
  }

  protected def initializeCustom(): Unit = self ! DoneWithOne

  protected def initializeIndex(indexName: String): Unit = {
    val receiver = self
    httpClient.get[Any](s"$elasticsearchUrl/$indexName", logError = false) onComplete {
      case Success(_) ⇒
      case _ ⇒
        receiver ! WaitForOne
        httpClient.put[Any](s"$elasticsearchUrl/$indexName", "") onComplete {
          _ ⇒ receiver ! DoneWithOne
        }
    }
  }
}
