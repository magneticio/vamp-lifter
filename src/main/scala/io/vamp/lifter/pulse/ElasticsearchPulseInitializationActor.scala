package io.vamp.lifter.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import com.sksamuel.elastic4s.http.{ ElasticClient, ElasticProperties }
import io.vamp.common.NamespaceProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.lifter.pulse.ElasticsearchPulseInitializationActor.TemplateDefinition
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.{ ElasticsearchClientAdapter, ElasticsearchPulseActor, ElasticsearchPulseEvent }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.io.Source

object ElasticsearchPulseInitializationActor {

  case class TemplateDefinition(name: String, template: String)

}

trait ElasticsearchPulseInitializationActor extends ElasticsearchPulseEvent with NamespaceValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  implicit def timeout: Timeout

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  private lazy val esClient = new ElasticsearchClientAdapter(ElasticClient(ElasticProperties(ElasticsearchPulseActor.elasticsearchUrl())))

  private def templates(version: Int): List[TemplateDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$version/$name.json")).mkString.replace("$NAME", indexName)

    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override lazy val indexTimeFormat: Map[String, String] = Map()

  override lazy val indexName: String = resolveWithNamespace(ElasticsearchPulseActor.indexName(), lookup = true)

  protected def initializeElasticsearch(): Future[Any] = initializeTemplates().flatMap(_ ⇒ initializeIndex(indexTypeName()._1))

  private def initializeTemplates(): Future[Any] = {
    esClient.version().flatMap {
      case Some(version) if version.take(1).toInt >= 6 ⇒ createTemplates(6)
      case _ ⇒ createTemplates(2)
    }
  }

  private def initializeIndex(indexName: String): Future[Any] = esClient.createIndex(indexName)

  private def createTemplates(version: Int): Future[Any] = {
    def createTemplate(definition: TemplateDefinition) = esClient.createIndexTemplate(definition.name, definition.template)

    Future.sequence(
      templates(version)
        .filter(definition => Await.result(esClient.templateExists(definition.name), timeout.duration))
        .map(createTemplate))
  }
}
