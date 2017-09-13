package io.vamp.lifter.http

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import io.vamp.common.akka.ActorBootstrap
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.{ ExecutionContext, Future }

class HttpApiBootstrap extends ActorBootstrap {

  private var binding: Option[Future[ServerBinding]] = None

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = Future.successful(Nil)

  override def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    super.start
    val (interface, port) = (Config.string("vamp.lifter.http-api.interface")(), Config.int("vamp.lifter.http-api.port")())
    implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
    info(s"Binding API: $interface:$port")
    binding = Option(Http().bindAndHandle(routes, interface, port))
  }

  override def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {}

  override def stop(implicit actorSystem: ActorSystem, namespace: Namespace): Future[Unit] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    binding.map {
      _.flatMap { server ⇒
        info(s"Unbinding API")
        server.unbind().flatMap {
          _ ⇒ Http().shutdownAllConnectionPools()
        }
      }.flatMap { _ ⇒ super.stop }
    } getOrElse super.stop
  }

  protected def routes(implicit namespace: Namespace, actorSystem: ActorSystem, materializer: Materializer): Route = new HttpApiRoute().routes
}
