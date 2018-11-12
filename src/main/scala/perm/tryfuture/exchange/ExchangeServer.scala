package perm.tryfuture.exchange

import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import perm.tryfuture.exchange.OrderBook.Trade

object ExchangeServer {
  val exchangeServerServiceKey = ServiceKey("ExchangeServer")

  def main(args: Array[String]): Unit = {
    ActorSystem(init(), Configs.actorSystemName, Configs.systemExchangeServer)
  }

  private[this] def notifyTradeExecuted(trade: Trade): Unit = {
    trade.buyOrder.replyTo ! ExecutedBuyOrder(trade.price, trade.quantity)
    trade.sellOrder.replyTo ! ExecutedSellOrder(trade.price, trade.quantity)
  }

  private[this] def mainBehavior(orderBook: OrderBook): Behavior[ExchangeServerCommand] = Behaviors.receiveMessage {
    case GetPrices(replyTo) =>
      val currentPrices = CurrentPrices(
        buyPrice = orderBook.buyOrders.headOption.map(_.price),
        sellPrice = orderBook.sellOrders.headOption.map(_.price)
      )
      if (ThreadLocalRandom.current().nextInt(100) == 0)
        println(currentPrices)
      replyTo ! currentPrices
      Behaviors.same
    case sellOrder: SellOrder =>
      if (sellOrder.price > 0 && sellOrder.price < 1000) {
        val newOrderBook = orderBook.processSellOrder(sellOrder)
        newOrderBook.trades.foreach(notifyTradeExecuted)
        mainBehavior(newOrderBook.forgetHistory)
      } else {
        Behaviors.same
      }
    case buyOrder: BuyOrder =>
      if (buyOrder.price > 0 && buyOrder.price < 1000) {
        val newOrderBook = orderBook.processBuyOrder(buyOrder)
        newOrderBook.trades.foreach(notifyTradeExecuted)
        mainBehavior(newOrderBook.forgetHistory)
      } else {
        Behaviors.same
      }
  }

  private def init(): Behavior[ExchangeServerCommand] = Behaviors.setup { ctx =>
    ctx.system.receptionist ! Receptionist.Register(exchangeServerServiceKey, ctx.self)
    mainBehavior(OrderBook.empty)
  }

  sealed trait ExchangeServerCommand

  sealed trait Order extends ExchangeServerCommand {
    def price: BigDecimal

    def quantity: Long
  }

  sealed trait ExecutedOrder {
    def price: BigDecimal

    def quantity: Long
  }

  final case class SellOrder(price: BigDecimal, quantity: Long, replyTo: ActorRef[ExecutedSellOrder]) extends Order with Ordered[SellOrder] {

    override def compare(that: SellOrder): Int = {
      if (this.price != that.price)
        this.price.compareTo(that.price)
      else
        this.replyTo.compareTo(that.replyTo)
    }
  }

  final case class BuyOrder(price: BigDecimal, quantity: Long, replyTo: ActorRef[ExecutedBuyOrder]) extends Order with Ordered[BuyOrder] {

    override def compare(that: BuyOrder): Int = {
      if (this.price != that.price)
        -this.price.compareTo(that.price)
      else
        this.replyTo.compareTo(that.replyTo)
    }
  }

  final case class ExecutedSellOrder(price: BigDecimal, quantity: Long) extends ExecutedOrder

  final case class ExecutedBuyOrder(price: BigDecimal, quantity: Long) extends ExecutedOrder

  final case class CurrentPrices(buyPrice: Option[BigDecimal], sellPrice: Option[BigDecimal])

  final case class GetPrices(replyTo: ActorRef[CurrentPrices]) extends ExchangeServerCommand

}
