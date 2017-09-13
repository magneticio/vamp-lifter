package io.vamp.lifter

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.bootstrap.{ ActorBootstrap, ConfigurationBootstrap, LoggingBootstrap }
import io.vamp.common.Namespace
import io.vamp.lifter.http.HttpApiBootstrap
import io.vamp.persistence.PersistenceBootstrap
import io.vamp.pulse.PulseBootstrap

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

object Lifter extends App {

  implicit val system: ActorSystem = ActorSystem("vamp-lifter")
  implicit val timeout: Timeout = Timeout(FiniteDuration(ConfigFactory.load().getDuration("vamp.lifter.bootstrap.timeout", MILLISECONDS), MILLISECONDS))

  protected lazy val bootstrap = {
    implicit val namespace: Namespace = Namespace(ConfigFactory.load().getString("vamp.namespace"))
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
      new ConfigurationBootstrap :+
      new ActorBootstrap(new PersistenceBootstrap :: new PulseBootstrap :: new HttpApiBootstrap :: Nil)
  }

  sys.addShutdownHook {
    bootstrap.reverse.foreach(_.stop())
    system.terminate()
  }

  bootstrap.foreach(_.start())
}
