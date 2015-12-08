package perm.tryfuture.exchange

import akka.actor.{Actor, ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

class NewsServer extends Actor {

  import NewsServer._

  var news = News(isGood = true)
  val tick = context.system.scheduler.schedule(100.millis, (Random.nextInt(1000) + 400).millis, self, "tick")

  override def receive: Receive = {
    case GetNews ⇒
      sender() ! news
    case "tick" ⇒
      // Сменить новость на новую
      news = News(isGood = Random.nextBoolean())
  }
}

object NewsServer {

  case object GetNews

  case class News(isGood: Boolean)

}

object NewsServersStarter extends App {
  val system = ActorSystem("akka-stock-exchange-news", Configs.systemNewsServers)
  (1 to Configs.numberOfNewsServers).map { case id ⇒
    system.actorOf(Props[NewsServer], s"newsServer-$id")
  }
  Await.result(system.whenTerminated, Duration.Inf)
}