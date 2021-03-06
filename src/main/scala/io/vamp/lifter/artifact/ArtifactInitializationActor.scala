package io.vamp.lifter.artifact

import java.nio.file.{ Path, Paths }

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Config
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ CommonActorLogging, CommonProvider, CommonSupportForActors }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.artifact.{ Breed, DefaultBreed, Deployable, Workflow }
import io.vamp.model.reader.WorkflowReader
import io.vamp.operation.controller.{ ArtifactApiController, DeploymentApiController }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future
import scala.io.Source

object ArtifactInitializationActor {

  case class Load(files: List[String])

}

class ArtifactInitializationActor extends ArtifactLoader with CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  implicit lazy val timeout: Timeout = PersistenceActor.timeout()

  def receive: Actor.Receive = {
    case Load(files) ⇒
      val receiver = sender()
      loadFiles()(Config.stringList("vamp.lifter.artifacts")().filter(files.contains)).foreach(_ ⇒ receiver ! true)
    case _ ⇒
  }
}

trait ArtifactLoader extends ArtifactApiController with DeploymentApiController {
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
    if (`type` == Breed.kind && fileName.endsWith(".js"))
      actorFor[PersistenceActor] ? PersistenceActor.Update(DefaultBreed(name, Map(), Deployable("application/javascript", source), Nil, Nil, Nil, Nil, Map(), None), Some(source))
    else if (`type` == Workflow.kind)
      actorFor[PersistenceActor] ? PersistenceActor.Update(WorkflowReader.read(source), Some(source))
    else
      updateArtifact(`type`, name, source, validateOnly = false)
  }
}
