package io.vamp.lifter.artifact

import java.nio.file.{ Path, Paths }

import akka.actor.Actor
import akka.util.Timeout
import io.vamp.common.akka.{ CommonActorLogging, CommonProvider, CommonSupportForActors }
import io.vamp.common.{ Config, RootAnyMap }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.artifact.{ Breed, DefaultBreed, Deployable, Workflow }
import io.vamp.model.reader.WorkflowReader
import io.vamp.operation.controller.{ ArtifactApiController, DeploymentApiController }
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

object ArtifactInitializationActor {

  case class Load(files: List[String])

}

class ArtifactInitializationActor extends ArtifactLoader with CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  implicit lazy val timeout: Timeout = Timeout(5.second)

  def receive: Actor.Receive = {
    case Load(files) ⇒
      val receiver = sender()
      loadFiles()(Config.stringList("vamp.lifter.artifacts")().filter(files.contains)).foreach(_ ⇒ receiver ! true)
    case _ ⇒
  }
}

trait ArtifactLoader extends ArtifactApiController with DeploymentApiController with VampJsonFormats {
  this: CommonActorLogging with CommonProvider ⇒

  implicit def timeout: Timeout

  protected def loadFiles(): List[String] ⇒ Future[Any] = { files ⇒
    val result = files.map(file ⇒ load(Paths.get(file), Source.fromFile(file).mkString))
    Future.sequence(result)
  }

  protected def load(path: Path, source: String): Future[Any] = {
    val `type` = path.getParent.getFileName.toString
    val fileName = path.getFileName.toString
    val name = fileName.substring(0, fileName.lastIndexOf("."))

    log.info(s"Persisting artifact: ${`type`}/$name")
    create(`type`, fileName, name, source)
  }

  protected def create(`type`: String, fileName: String, name: String, source: String): Future[Any] = {
    if (`type` == Breed.kind && fileName.endsWith(".js")) {
      val newBreed = DefaultBreed(name, RootAnyMap.empty, Deployable("application/javascript", source), Nil, Nil, Nil, Nil, Map(), None)
      VampPersistence().createOrUpdate[Breed](newBreed)
    } else if (`type` == Workflow.kind) {
      val newWorkflow = WorkflowReader.read(source)
      VampPersistence().createOrUpdate[Workflow](newWorkflow)
    } else
      updateArtifact(`type`, name, source, validateOnly = false)
  }
}
