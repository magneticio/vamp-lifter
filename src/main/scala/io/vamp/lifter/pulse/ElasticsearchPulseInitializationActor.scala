package io.vamp.lifter.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.NotificationProvider
import io.vamp.lifter.pulse.ElasticsearchPulseInitializationActor.TemplateDefinition
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.{ ElasticsearchPulseActor, ElasticsearchPulseEvent }

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

object ElasticsearchPulseInitializationActor {

  case class TemplateDefinition(name: String, template: String)

}

trait ElasticsearchPulseInitializationActor extends ElasticsearchPulseEvent with NamespaceValueResolver {
  this: NotificationProvider ⇒

  implicit def timeout: Timeout

  implicit def namespace: Namespace

  implicit def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  private lazy val httpClient = new HttpClient

  private lazy val elasticsearchUrl: String = ElasticsearchPulseActor.elasticsearchUrl()

  private lazy val templates: List[TemplateDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString.replace("$NAME", indexName)

    List("template", "template-event").map(template ⇒ TemplateDefinition(s"$indexName-$template", load(template)))
  }

  override lazy val indexTimeFormat: Map[String, String] = Map()

  override lazy val indexName: String = resolveWithNamespace(ElasticsearchPulseActor.indexName(), lookup = true)

  protected def initializeElasticsearch(): Future[Any] = initializeTemplates().flatMap(_ ⇒ initializeIndex(indexTypeName()._1))

  private def initializeTemplates(): Future[Any] = {

    def createTemplate(definition: TemplateDefinition) = httpClient.put[Any](s"$elasticsearchUrl/_template/${definition.name}", definition.template)

    httpClient.get[Any](s"$elasticsearchUrl/_template") map {
      case map: Map[_, _] ⇒ templates.filterNot(definition ⇒ map.asInstanceOf[Map[String, Any]].contains(definition.name)).map(createTemplate)
      case _              ⇒ templates.map(createTemplate)
    } flatMap (futures ⇒ Future.sequence(futures))
  }

  private def initializeIndex(indexName: String): Future[Any] = {
    httpClient.get[Any](s"$elasticsearchUrl/$indexName", logError = false) recoverWith {
      case _ ⇒ httpClient.put[Any](s"$elasticsearchUrl/$indexName", "")
    }
  }
}
