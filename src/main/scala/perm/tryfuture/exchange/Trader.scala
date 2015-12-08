package perm.tryfuture.exchange

import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class Trader(server: ActorRef, newsSites: Iterable[ActorRef]) extends Actor {
  val tick = context.system.scheduler.schedule(1000.millis, 1000.millis, self, "tick")

  override def receive = {
    case "tick" ⇒
      implicit val timeout = new Timeout(100.millis)
      val amount = Random.nextInt(200)
      // Запрашиваем список новостей
      val news = newsSites.map(ask(_, NewsServer.GetNews).mapTo[NewsServer.News])
      // Дожидаемся всех ответов и выбираем случайную
      // Можно заменить на выбор самой популярной
      Future.sequence(news).map(Random.shuffle(_).headOption.foreach { case news: NewsServer.News ⇒
        if (news.isGood) {
          // Если новость хорошая, то лучше покупать
          ask(server, ExchangeServer.GetRates).mapTo[ExchangeServer.Rates].map {
            case ExchangeServer.Rates(_, br: ExchangeServer.BuyRate) ⇒
              // Предлагаем цену иногда лучшую, чем самая лучшая текущая цена
              server ! ExchangeServer.Buy(br.rate - 40 + Random.nextInt(80), amount)
            case _ ⇒
              // Для инициализации торгов
              server ! ExchangeServer.Buy(Random.nextInt(100) + 400, amount)
          }
        } else {
          ask(server, ExchangeServer.GetRates).mapTo[ExchangeServer.Rates].map {
            case ExchangeServer.Rates(sr: ExchangeServer.SellRate, _) ⇒
              server ! ExchangeServer.Sell(sr.rate + 40 - Random.nextInt(80), amount)
            case _ ⇒
              server ! ExchangeServer.Sell(Random.nextInt(100) + 400, amount)
          }
        }
      })
  }
}

object TraderStarter extends App {
  val system = ActorSystem("akka-stock-exchange-traders", Configs.systemTraders)
  implicit val timeout = new Timeout(1000.millis)
  val newsServersFuture = Future.sequence((1 to Configs.numberOfNewsServers).map { case id ⇒
    // Получить ссылки на новостные сайты
    system.actorSelection(s"akka.tcp://akka-stock-exchange-news@127.0.0.1:59002/user/newsServer-$id").resolveOne()
  })
  // Получить ссылку на сервер биржи
  val exchangeServerFuture = system.actorSelection("akka.tcp://akka-stock-exchange-xServer@127.0.0.1:59001/user/exchangeServer").resolveOne()
  for {
    server <- exchangeServerFuture
    newsServers <- newsServersFuture
    // Запускаем много трейдеров
    _ <- 1 to 1000
  } {
    system.actorOf(Props(classOf[Trader], server, newsServers))
  }
  Await.result(system.whenTerminated, Duration.Inf)
}