package perm.tryfuture.exchange

import javafx.application.Application
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.chart.{LineChart, NumberAxis, XYChart}
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.{Stage, WindowEvent}

import akka.actor.{ActorSystem, Props}

object UI {
  def main(args: Array[String]) {
    Application.launch(classOf[UI], args: _*)
  }
}

class UI extends Application {
  val buysSeries = new XYChart.Series[Number, Number]
  val sellsSeries = new XYChart.Series[Number, Number]
  val newsSeries = new XYChart.Series[Number, Number]

  def setNumberOfPositiveNews(tick: Long, newsRate: Int) = {
    newsSeries.getData.add(new XYChart.Data(tick, newsRate))
  }

  def setRates(tick: Long, sellRate: Int, buyRate: Int) = {
    buysSeries.getData.add(new XYChart.Data(tick, buyRate))
    sellsSeries.getData.add(new XYChart.Data(tick, sellRate))
    println(buyRate + " " + sellRate)
  }

  override def start(primaryStage: Stage) {
    // Настройка интерфейса
    primaryStage.setTitle("Akka stock exchange demo")
    val vbox = new VBox()
    buysSeries.setName("Buy rate")
    sellsSeries.setName("Sells rate")
    newsSeries.setName("News OK?")
    val xAxisPrice: NumberAxis = new NumberAxis
    val yAxisPrice: NumberAxis = new NumberAxis
    xAxisPrice.setLabel("Tick")
    yAxisPrice.setLabel("Price")
    val xAxisNews: NumberAxis = new NumberAxis
    val yAxisNews: NumberAxis = new NumberAxis
    xAxisNews.setLabel("Tick")
    yAxisNews.setLabel("# of OK news")
    val lineChartPrice: LineChart[Number, Number] = new LineChart[Number, Number](xAxisPrice, yAxisPrice)
    val lineChartNews: LineChart[Number, Number] = new LineChart[Number, Number](xAxisNews, yAxisNews)
    lineChartPrice.getData.add(buysSeries)
    lineChartPrice.getData.add(sellsSeries)
    lineChartNews.getData.add(newsSeries)
    vbox.getChildren.addAll(lineChartPrice, lineChartNews, new Text("Russia, Perm, 2015"))
    primaryStage.setScene(new Scene(vbox))

    val system = ActorSystem("akka-stock-exchange-ui", Configs.systemUi)
    system.actorOf(Props(classOf[StatisticsCollector], this))

    primaryStage.setOnCloseRequest(new EventHandler[WindowEvent]() {
      def handle(we: WindowEvent) {
        // При закрытии формы завершить и приложение
        system.terminate()
      }
    })
    primaryStage.show()
  }
}