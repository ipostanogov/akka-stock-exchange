package perm.tryfuture.exchange

import javafx.application.Platform

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class StatisticsCollector(ui: UI) extends Actor {
  implicit val timeout = new Timeout(1000.millis)
  // Получить ссылку на сервер биржи
  val exchangeServerFuture = context.system.actorSelection("akka.tcp://akka-stock-exchange-xServer@127.0.0.1:59001/user/exchangeServer").resolveOne()
  val newsServersFuture = Future.sequence((1 to Configs.numberOfNewsServers).map { case id ⇒
    // Получить ссылки на новостные сайты
    context.system.actorSelection(s"akka.tcp://akka-stock-exchange-news@127.0.0.1:59002/user/newsServer-$id").resolveOne()
  })
  val tick = context.system.scheduler.schedule(1000.millis, 1000.millis, self, "tick")
  var tickCount = 0

  override def receive: Receive = {
    case "tick" ⇒
      tickCount += 1
      implicit val timeout = new Timeout(100.millis)
      // Запрашиваем новости
      val news = newsServersFuture.map(_.map(ask(_, NewsServer.GetNews).mapTo[NewsServer.News])).flatMap(Future.sequence(_))
      // Суммируем новости как целочисленные значения (для простоты отображения)
      val newsAsInt = news.map(_.map(n ⇒ if (n.isGood) 1 else 0))
      val rates = exchangeServerFuture.flatMap(ask(_, ExchangeServer.GetRates).mapTo[ExchangeServer.Rates])
      for {
        numberOfPositiveNews <- newsAsInt.map(_.sum)
      } {
        // Обновление UI должно быть в потоке JavaFX
        Platform.runLater(new Runnable {
          override def run(): Unit = {
            ui.setNumberOfPositiveNews(tickCount, numberOfPositiveNews)
          }
        })
      }
      rates.foreach {
        case ExchangeServer.Rates(sr: ExchangeServer.SellRate, br: ExchangeServer.BuyRate) ⇒
          Platform.runLater(new Runnable {
            override def run(): Unit = {
              ui.setRates(tickCount, sr.rate, br.rate)
            }
          })
      }
  }
}