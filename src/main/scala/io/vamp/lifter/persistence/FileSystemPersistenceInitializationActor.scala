package io.vamp.lifter.persistence

import java.io.File

import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.model.resolver.NamespaceValueResolver

class FileSystemPersistenceInitializationActor extends CommonSupportForActors
    with NamespaceValueResolver
    with LifterNotificationProvider {

  override def receive: Receive = {
    case "init" ⇒
      val filePath: String = Config.string("vamp.persistence.database.filesystem.path")()
      val file = new File(filePath)
      if (!file.exists()) self ! "init"
  }

  override def preStart(): Unit = self ! "init"
}
