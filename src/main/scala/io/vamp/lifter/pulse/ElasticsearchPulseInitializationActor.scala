package io.vamp.lifter.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import com.sksamuel.elastic4s.http.{ ElasticClient, ElasticDsl, ElasticProperties }
import com.sksamuel.elastic4s.mappings.MappingDefinition
import io.vamp.common.NamespaceProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.lifter.pulse.ElasticsearchPulseInitializationActor.TemplateDefinition
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.ElasticsearchClientAdapter._
import io.vamp.pulse.{ ElasticsearchClientAdapter, ElasticsearchPulseActor, ElasticsearchPulseEvent }

import scala.collection.immutable.Seq
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.io.Source

object ElasticsearchPulseInitializationActor {

  case class TemplateDefinition(name: String, pattern: String, mappings: Seq[MappingDefinition], order: Int)

}

trait ElasticsearchPulseInitializationActor extends ElasticsearchPulseEvent with NamespaceValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  implicit def timeout: Timeout

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  private lazy val esClient = new ElasticsearchClientAdapter(ElasticClient(ElasticProperties(ElasticsearchPulseActor.elasticsearchUrl())))

  private def templates(version: Int): List[TemplateDefinition] = {
    def loadMappings(name: String) = {
      val mapping = Source.fromInputStream(getClass.getResourceAsStream(s"$version/$name.json")).mkString
      Seq(ElasticDsl.mapping("_default_").rawSource(mapping))
    }

    List(
      TemplateDefinition(name = s"$indexName-template", pattern = s"$indexName-*", mappings = loadMappings("template"), order = 1),
      TemplateDefinition(name = s"$indexName-template-event", pattern = s"$indexName-*", mappings = loadMappings("template-event"), order = 2)
    )
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
    def createTemplate(definition: TemplateDefinition): Future[ElasticsearchCreateTemplateResponse] =
      esClient.createIndexTemplate(definition.name, definition.pattern, definition.order, definition.mappings)

    val createTemplatesResponses: Seq[Future[ElasticsearchCreateTemplateResponse]] = templates(version)
      .filter(definition ⇒ Await.result(esClient.templateExists(definition.name), timeout.duration))
      .map(createTemplate)

    Future.sequence(createTemplatesResponses)
  }
}
