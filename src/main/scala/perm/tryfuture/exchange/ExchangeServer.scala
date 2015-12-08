package perm.tryfuture.exchange

import akka.actor._

import scala.concurrent.Await
import scala.concurrent.duration._

class ExchangeServer extends Actor with ActorLogging {

  import ExchangeServer._

  // Список ордеров на продажу и покупку
  // Ордера на продажу отсортированы от меньшего к большему
  // На покупку - наоборот
  private var sells = collection.SortedSet[ActorSell]()
  private var buys = collection.SortedSet[ActorBuy]()

  override def receive: Receive = {
    case GetRates ⇒
      // Ответить самой дешёвой ценой на продажу и самой дорогой на покупку (если есть)
      sender() ! Rates(
        sellRate = sells.headOption.map(_.sell.rate).map(SellRate).getOrElse(NoTrades),
        buyRate = buys.headOption.map(_.buy.rate).map(BuyRate).getOrElse(NoTrades)
      )
    case sell: Sell ⇒
      sells.headOption match {
        // Если новый ордер хуже имеющихся, то просто его запомним
        case Some(ActorSell(hSell, _)) if sell.rate > hSell.rate ⇒
          sells += ActorSell(sell, sender())
        // Иначе, возможно, его можно исполнить
        case _ ⇒
          doSell(sell, sender())
      }
    case buy: Buy ⇒
      buys.headOption match {
        case Some(ActorBuy(hBuy, _)) if buy.rate < hBuy.rate ⇒
          buys += ActorBuy(buy, sender())
        case _ ⇒
          doBuy(buy, sender())
      }
  }

  def doSell(sell: Sell, seller: ActorRef): Unit = {
    buys.headOption match {
      // Если есть ордер на покупку купить по цене, не меньшей, чем та, за которую готовы продать
      case Some(oldBuy@ActorBuy(buy, buyer)) if buy.rate >= sell.rate ⇒
        // Если готовы купить больше, чем предлагается
        if (buy.amount >= sell.amount) {
          // Продать
          seller ! Sold(buy.rate, sell.amount)
          buyer ! Bought(buy.rate, sell.amount)
          buys -= oldBuy
          // Если старый ордер на покупку полностью не закрыт, то добавить новый на меньший объём
          if (buy.amount - sell.amount > 0)
            buys += ActorBuy(Buy(buy.rate, buy.amount - sell.amount), buyer)
        } else {
          // Иначе полностью закрыть ордер на покупку
          seller ! Sold(buy.rate, buy.amount)
          buyer ! Bought(buy.rate, buy.amount)
          buys -= oldBuy
          // Допродать оставшийся объём
          doSell(Sell(sell.rate, sell.amount - buy.amount), seller)
        }
      case _ ⇒
        // Просто запомнить
        sells += ActorSell(sell, seller)
    }
  }

  def doBuy(buy: Buy, buyer: ActorRef): Unit = {
    sells.headOption match {
      case Some(oldSell@ActorSell(sell, seller)) if buy.rate >= sell.rate ⇒
        if (sell.amount >= buy.amount) {
          seller ! Sold(sell.rate, buy.amount)
          buyer ! Bought(sell.rate, buy.amount)
          sells -= oldSell
          if (sell.amount - buy.amount > 0)
            sells += ActorSell(Sell(sell.rate, sell.amount - buy.amount), seller)
        } else {
          seller ! Sold(sell.rate, sell.amount)
          buyer ! Bought(sell.rate, sell.amount)
          sells -= oldSell
          doBuy(Buy(buy.rate, buy.amount - sell.amount), buyer)
        }
      case _ ⇒
        buys += ActorBuy(buy, buyer)
    }
  }
}

object ExchangeServer {

  // Сообщения, посылаемые серверу

  case class Sell(rate: Int, amount: Int)

  case class Buy(rate: Int, amount: Int)

  private case class ActorSell(sell: Sell, sender: ActorRef) extends Ordered[ActorSell] {
    override def compare(that: ActorSell): Int = {
      if (this.sell.rate != that.sell.rate)
        this.sell.rate.compare(that.sell.rate)
      else
        this.sender.compareTo(that.sender)
    }
  }

  private case class ActorBuy(buy: Buy, sender: ActorRef) extends Ordered[ActorBuy] {
    override def compare(that: ActorBuy): Int = {
      if (this.buy.rate != that.buy.rate)
        -this.buy.rate.compare(that.buy.rate)
      else
        this.sender.compareTo(that.sender)
    }
  }

  case class Sold(rate: Int, amount: Int)

  case class Bought(rate: Int, amount: Int)

  case object GetRates

  sealed trait TradeRate

  case object NoTrades extends TradeRate

  case class BuyRate(rate: Int) extends TradeRate

  case class SellRate(rate: Int) extends TradeRate

  case class Rates(sellRate: TradeRate, buyRate: TradeRate)

}

object ExchangeServerStarter extends App {
  val system = ActorSystem("akka-stock-exchange-xServer", Configs.systemExchangeServer)
  system.actorOf(Props[ExchangeServer], "exchangeServer")
  Await.result(system.whenTerminated, Duration.Inf)
}