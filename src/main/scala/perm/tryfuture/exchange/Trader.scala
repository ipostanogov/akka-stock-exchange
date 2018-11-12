package perm.tryfuture.exchange

import java.util.concurrent.ThreadLocalRandom

import akka.actor.Scheduler
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Subscribe
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math.BigDecimal.RoundingMode
import scala.util.Random

object Trader {

  def main(args: Array[String]): Unit = {
    ActorSystem(init(), Configs.actorSystemName, Configs.systemTraders)
  }

  private[this] def init(): Behavior[TraderCommand] = Behaviors.setup { ctx =>
    val listingResponseMapper: ActorRef[Receptionist.Listing] = ctx.messageAdapter(listing => ListingEvent(listing))
    ctx.system.receptionist ! Subscribe(ExchangeServer.exchangeServerServiceKey, listingResponseMapper)
    ctx.system.receptionist ! Subscribe(NewsServer.newsServerServiceKey, listingResponseMapper)
    setup(None, Set())
  }

  private[this] def setup(exchangeServer: Option[ActorRef[ExchangeServer.ExchangeServerCommand]], newsServers: Set[ActorRef[NewsServer.NewsServerCommand]]): Behavior[TraderCommand] = Behaviors.setup { ctx =>
    exchangeServer match {
      case Some(exServer) if newsServers.size == Configs.numberOfNewsServers =>
        for (_ <- 1 to 100)
          ctx.spawnAnonymous(mainBehavior(exServer, newsServers))
        Behaviors.ignore
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

  private[this] def scheduleTick(ctx: ActorContext[TraderCommand]) = {
    ctx.scheduleOnce(2.seconds, ctx.self, Tick)
  }

  private[this] def mainBehavior(exchangeServer: ActorRef[ExchangeServer.ExchangeServerCommand], newsServers: Set[ActorRef[NewsServer.NewsServerCommand]]): Behavior[TraderCommand] = Behaviors.setup { ctx =>
    scheduleTick(ctx)
    val exServerResponseMapper: ActorRef[ExchangeServer.ExecutedOrder] = ctx.messageAdapter(exOrder => WrappedExecutedOrder(exOrder))
    implicit val timeout: Timeout = 1.second
    implicit val scheduler: Scheduler = ctx.system.scheduler
    implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
    Behaviors.receiveMessage {
      case Tick =>
        val newsF: Set[Future[NewsServer.News]] = newsServers.map(_ ? NewsServer.GetNews)
        val randomSortedNewsF: Future[List[NewsServer.News]] = Future.sequence(newsF.toList).map(Random.shuffle(_))
        val pricesF: Future[ExchangeServer.CurrentPrices] = exchangeServer ? ExchangeServer.GetPrices
        pricesF.zip(randomSortedNewsF).collect {
          case (prices, chosenNews :: _) =>
            implicit class RichBigDecimal(bd: BigDecimal) {
              def ceil: BigDecimal = bd.setScale(0, RoundingMode.CEILING)
            }

            val random = ThreadLocalRandom.current()
            val quantity = random.nextLong(200) + 1
            val priceModifier = 1.0 + random.nextGaussian() * 0.07
            val fairValue = BigDecimal("500")
            if (chosenNews.isGood) {
              prices match {
                case ExchangeServer.CurrentPrices(_, Some(sellPrice)) =>
                  exchangeServer ! ExchangeServer.BuyOrder((sellPrice * priceModifier * 0.99).ceil, quantity, exServerResponseMapper)
                case _ =>
                  exchangeServer ! ExchangeServer.BuyOrder((fairValue * priceModifier).ceil, quantity, exServerResponseMapper)
              }
            } else {
              prices match {
                case ExchangeServer.CurrentPrices(Some(buyPrice), _) =>
                  exchangeServer ! ExchangeServer.SellOrder((buyPrice * priceModifier * 1.01).ceil, quantity, exServerResponseMapper)
                case _ =>
                  exchangeServer ! ExchangeServer.SellOrder((fairValue * priceModifier).ceil, quantity, exServerResponseMapper)
              }
            }
        } onComplete { _ => scheduleTick(ctx) }
        Behaviors.same
      case _ =>
        Behaviors.same
    }
  }

  private[this] sealed trait TraderCommand

  private[this] final case class ListingEvent(listing: Receptionist.Listing) extends TraderCommand

  private[this] final case class WrappedExecutedOrder(order: ExchangeServer.ExecutedOrder) extends TraderCommand

  private[this] final case object Tick extends TraderCommand

}
