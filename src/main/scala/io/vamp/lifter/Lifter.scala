package io.vamp.lifter

import akka.pattern.ask
import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.bootstrap.{ ActorBootstrap, LoggingBootstrap, RestartableActorBootstrap }
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.gateway_driver.GatewayDriverBootstrap
import io.vamp.lifter.http.HttpApiBootstrap
import io.vamp.lifter.operation.{ ConfigurationActor, LifterBootstrap }
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap
import io.vamp.workflow_driver.WorkflowDriverBootstrap

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

object Lifter extends App {

  private val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("vamp-lifter")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val namespace: Namespace = Namespace(config.getString("vamp.namespace"))
  implicit val timeout: Timeout = Timeout(FiniteDuration(config.getDuration("vamp.lifter.bootstrap.timeout", MILLISECONDS), MILLISECONDS))

  protected lazy val bootstrap = {
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
      new ActorBootstrap(new LifterBootstrap(argument("initialize")) :: new HttpApiBootstrap :: Nil)
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.stop())
    system.terminate()
  }

  IoC.createActor[ConfigurationActor](ConfigurationActor.filterVampNoLifter, false).flatMap { _ ? ConfigurationActor.Init(namespace) } foreach { _ ⇒ bootstrap.foreach(_.start()) }

  def argument(name: String): Boolean = args.map(_.stripMargin('-').trim).contains(name)
}
