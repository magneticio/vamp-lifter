package io.vamp.lifter

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.bootstrap.{ ActorBootstrap, LoggingBootstrap, RestartableActorBootstrap, VampApp }
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.gateway_driver.GatewayDriverBootstrap
import io.vamp.lifter.http.HttpApiBootstrap
import io.vamp.lifter.operation.{ ConfigActor, ConfigActorArgs, LifterBootstrap }
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap
import io.vamp.workflow_driver.WorkflowDriverBootstrap

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

object Lifter extends VampApp {

  private val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("vamp-lifter")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val namespace: Namespace = Namespace(config.getString("vamp.namespace"))
  implicit val timeout: Timeout = Timeout(FiniteDuration(config.getDuration("vamp.lifter.bootstrap.timeout", MILLISECONDS), MILLISECONDS))

  protected lazy val bootstraps = {
    List() :+
      new LoggingBootstrap {
        lazy val logo: String =
          s"""
             |██╗   ██╗ █████╗ ███╗   ███╗██████╗     ██╗     ██╗███████╗████████╗███████╗██████╗
             |██║   ██║██╔══██╗████╗ ████║██╔══██╗    ██║     ██║██╔════╝╚══██╔══╝██╔════╝██╔══██╗
             |██║   ██║███████║██╔████╔██║██████╔╝    ██║     ██║█████╗     ██║   █████╗  ██████╔╝
             |╚██╗ ██╔╝██╔══██║██║╚██╔╝██║██╔═══╝     ██║     ██║██╔══╝     ██║   ██╔══╝  ██╔══██╗
             | ╚████╔╝ ██║  ██║██║ ╚═╝ ██║██║         ███████╗██║██║        ██║   ███████╗██║  ██║
             |  ╚═══╝  ╚═╝  ╚═╝╚═╝     ╚═╝╚═╝         ╚══════╝╚═╝╚═╝        ╚═╝   ╚══════╝╚═╝  ╚═╝
             |                                                                      $version
             |                                                                      by magnetic.io
             |""".stripMargin
      } :+
      new RestartableActorBootstrap(namespace)(
        new PersistenceBootstrap :: new PulseBootstrap :: new ContainerDriverBootstrap :: new GatewayDriverBootstrap :: new WorkflowDriverBootstrap :: Nil
      ) :+
      new ActorBootstrap(new LifterBootstrap :: new HttpApiBootstrap :: Nil)
  }

  addShutdownBootstrapHook()

  IoC.createActor[ConfigActor](ConfigActorArgs()).flatMap(_ ? ConfigActor.Init(namespace)).flatMap(_ ⇒ startBootstraps())
}
