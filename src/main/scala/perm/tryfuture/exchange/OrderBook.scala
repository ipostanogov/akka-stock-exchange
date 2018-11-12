package perm.tryfuture.exchange

import perm.tryfuture.exchange.ExchangeServer.{BuyOrder, SellOrder}
import perm.tryfuture.exchange.OrderBook.Trade

import scala.collection.SortedSet

case class OrderBook(buyOrders: SortedSet[BuyOrder], sellOrders: SortedSet[SellOrder], trades: List[Trade]) {
  def processBuyOrder(buyOrder: BuyOrder): OrderBook = {
    sellOrders.headOption match {
      case Some(sellOrder) if buyOrder.price >= sellOrder.price =>
        if (sellOrder.quantity >= buyOrder.quantity) {
          val trade = Trade(sellOrder.price, buyOrder.quantity, buyOrder, sellOrder)
          if (sellOrder.quantity > buyOrder.quantity) {
            val sellRestOrder = sellOrder.copy(quantity = sellOrder.quantity - buyOrder.quantity)
            OrderBook(buyOrders, sellOrders - sellOrder + sellRestOrder, trade :: trades)
          } else {
            OrderBook(buyOrders, sellOrders - sellOrder, trade :: trades)
          }
        } else {
          val trade = Trade(sellOrder.price, sellOrder.quantity, buyOrder, sellOrder)
          val buyRestOrder = buyOrder.copy(quantity = buyOrder.quantity - sellOrder.quantity)
          OrderBook(buyOrders, sellOrders - sellOrder, trade :: trades).processBuyOrder(buyRestOrder)
        }
      case _ =>
        this.copy(buyOrders = buyOrders + buyOrder)
    }
  }

  def processSellOrder(sellOrder: SellOrder): OrderBook = {
    buyOrders.headOption match {
      case Some(buyOrder) if sellOrder.price <= buyOrder.price =>
        if (buyOrder.quantity >= sellOrder.quantity) {
          val trade = Trade(buyOrder.price, sellOrder.quantity, buyOrder, sellOrder)
          if (buyOrder.quantity > sellOrder.quantity) {
            val buyRestOrder = buyOrder.copy(quantity = buyOrder.quantity - sellOrder.quantity)
            OrderBook(buyOrders - buyOrder + buyRestOrder, sellOrders, trade :: trades)
          } else {
            OrderBook(buyOrders - buyOrder, sellOrders, trade :: trades)
          }
        } else {
          val trade = Trade(buyOrder.price, buyOrder.quantity, buyOrder, sellOrder)
          val sellRestOrder = sellOrder.copy(quantity = sellOrder.quantity - buyOrder.quantity)
          OrderBook(buyOrders - buyOrder, sellOrders, trade :: trades).processSellOrder(sellRestOrder)
        }
      case _ =>
        this.copy(sellOrders = sellOrders + sellOrder)
    }
  }

  def forgetHistory: OrderBook = {
    copy(trades = List())
  }
}

object OrderBook {

  def empty = OrderBook(SortedSet(), SortedSet(), List())

  case class Trade(price: BigDecimal, quantity: Long, buyOrder: BuyOrder, sellOrder: SellOrder)

}

