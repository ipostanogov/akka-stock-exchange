package perm.tryfuture.exchange

import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.typesafe.config.Config

import scala.concurrent.duration._

object NewsServer {
  val newsServerServiceKey = ServiceKey("NewsServer")

  def main(args: Array[String]): Unit = {
    ActorSystem(spawnMultipleNewsServers(), Configs.actorSystemName, Configs.systemNewsServers)
  }

  private[this] def spawnMultipleNewsServers(): Behavior[NotUsed] = Behaviors.setup { ctx =>
    for (_ <- 1 to Configs.numberOfNewsServers)
      ctx.spawnAnonymous(init(), DispatcherSelector.fromConfig("news-server-dispatcher"))
    Behaviors.ignore
  }

  private[this] def scheduleTick(ctx: ActorContext[NewsServerCommand]) = {
    ctx.scheduleOnce((800 + ThreadLocalRandom.current().nextInt(400)).millis, ctx.self, Tick)
  }

  private[this] def init(): Behavior[NewsServerCommand] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Register(newsServerServiceKey, ctx.self)
    scheduleTick(ctx)
    mainBehavior(News(isGood = true), System.currentTimeMillis())
  }

  private[this] def mainBehavior(currentNews: News, launchTime: Long): Behavior[NewsServerCommand] = Behaviors.receive { (ctx, message) =>
    message match {
      case GetNews(replyTo) =>
        replyTo ! currentNews
        Behaviors.same
      case Tick =>
        scheduleTick(ctx)
        val modifiedNewsStatus = if ((launchTime / 10000) % 2 == 0) {
          ThreadLocalRandom.current().nextBoolean() || ThreadLocalRandom.current().nextBoolean()
        } else {
          ThreadLocalRandom.current().nextBoolean() && ThreadLocalRandom.current().nextBoolean()
        }
        mainBehavior(News(isGood = modifiedNewsStatus), System.currentTimeMillis())
    }
  }

  sealed trait NewsServerCommand

  final case class GetNews(replyTo: ActorRef[News]) extends NewsServerCommand

  final case class News(isGood: Boolean)

  private[this] class NewsServerMailbox(settings: akka.actor.ActorSystem.Settings, config: Config) extends UnboundedStablePriorityMailbox(
    PriorityGenerator {
      case Tick => 0
      case _ => 1
    })

  private final case object Tick extends NewsServerCommand

}
