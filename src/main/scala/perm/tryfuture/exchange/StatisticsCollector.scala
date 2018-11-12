package perm.tryfuture.exchange

import akka.actor.Scheduler
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import javafx.application.Platform

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class StatisticsCollector(ui: UI) {

  import StatisticsCollector._

  val actorSystem = ActorSystem(init(), Configs.actorSystemName, Configs.systemUi)

  private[this] val launchTime = System.currentTimeMillis()

  private[this] def init(): Behavior[StatisticsCollectorCommand] = Behaviors.setup { ctx =>
    val listingResponseMapper: ActorRef[Receptionist.Listing] = ctx.messageAdapter(listing => ListingEvent(listing))
    ctx.system.receptionist ! Subscribe(ExchangeServer.exchangeServerServiceKey, listingResponseMapper)
    ctx.system.receptionist ! Subscribe(NewsServer.newsServerServiceKey, listingResponseMapper)
    setup(None, Set())
  }

  private[this] def setup(exchangeServer: Option[ActorRef[ExchangeServer.ExchangeServerCommand]], newsServers: Set[ActorRef[NewsServer.NewsServerCommand]]): Behavior[StatisticsCollectorCommand] = Behaviors.setup { ctx =>
    exchangeServer match {
      case Some(exServer) if newsServers.size == Configs.numberOfNewsServers =>
        scheduleTick(ctx)
        mainBehavior(exServer, newsServers)
      case _ =>
        Behaviors.receiveMessage {
          case ListingEvent(listing) =>
            listing match {
              case ExchangeServer.exchangeServerServiceKey.Listing(lst) =>
                setup(lst.headOption.map(_.asInstanceOf[ActorRef[ExchangeServer.ExchangeServerCommand]]), newsServers)
              case NewsServer.newsServerServiceKey.Listing(lst) =>
                setup(exchangeServer, lst.map(_.asInstanceOf[ActorRef[NewsServer.NewsServerCommand]]))
            }
          case _ =>
            Behaviors.same
        }
    }
  }

  private[this] def scheduleTick(ctx: ActorContext[StatisticsCollectorCommand]) = {
    ctx.scheduleOnce(1.second, ctx.self, Tick)
  }

  private[this] def mainBehavior(exchangeServer: ActorRef[ExchangeServer.ExchangeServerCommand], newsServers: Set[ActorRef[NewsServer.NewsServerCommand]]): Behavior[StatisticsCollectorCommand] = Behaviors.setup { ctx =>
    implicit val timeout: Timeout = 1.second
    implicit val scheduler: Scheduler = ctx.system.scheduler
    implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
    Behaviors.receiveMessage {
      case Tick =>
        val newsF: Set[Future[NewsServer.News]] = newsServers.map(_ ? NewsServer.GetNews)
        val pricesF: Future[ExchangeServer.CurrentPrices] = exchangeServer ? ExchangeServer.GetPrices
        pricesF.zip(Future.sequence(newsF.toList)).collect {
          case (prices, news) =>
            val time = (System.currentTimeMillis() - launchTime) / 100
            val newsAsInt = news.map(n => if (n.isGood) 1 else 0).sum
            Platform.runLater(() => {
              ui.setRates(time, prices.buyPrice, prices.sellPrice)
              ui.setNumberOfPositiveNews(time, newsAsInt)
            })
        } onComplete { _ => scheduleTick(ctx) }
        Behaviors.same
      case _ =>
        Behaviors.same
    }
  }
}

object StatisticsCollector {

  sealed trait StatisticsCollectorCommand

  private final case class ListingEvent(listing: Receptionist.Listing) extends StatisticsCollectorCommand

  private final case object Tick extends StatisticsCollectorCommand

}
