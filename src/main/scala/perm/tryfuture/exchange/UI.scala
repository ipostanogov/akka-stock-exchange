package perm.tryfuture.exchange

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.chart.{LineChart, NumberAxis, XYChart}
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage

object UI {
  def main(args: Array[String]): Unit = {
    Application.launch(classOf[UI], args: _*)
  }
}

class UI extends Application {
  private[this] val buysSeries = new XYChart.Series[Number, Number]
  private[this] val sellsSeries = new XYChart.Series[Number, Number]
  private[this] val newsSeries = new XYChart.Series[Number, Number]

  def setNumberOfPositiveNews(time: Long, newsRate: Int): Boolean = {
    newsSeries.getData.add(new XYChart.Data(time, newsRate))
  }

  def setRates(tick: Long, buyRateOpt: Option[BigDecimal], sellRateOpt: Option[BigDecimal]): Unit = {
    buyRateOpt.map(buyRate => buysSeries.getData.add(new XYChart.Data(tick, buyRate)))
    sellRateOpt.map(sellRate => sellsSeries.getData.add(new XYChart.Data(tick, sellRate)))
    println(s"$tick ${buyRateOpt.getOrElse("-")} ${sellRateOpt.getOrElse("-")}")
  }

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Akka stock exchange demo")
    val vbox = new VBox()
    buysSeries.setName("Buys")
    sellsSeries.setName("Sells")
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
    vbox.getChildren.addAll(lineChartPrice, lineChartNews, new Text("Russia, Perm, 2018"))
    primaryStage.setScene(new Scene(vbox))

    val statisticsCollector = new StatisticsCollector(this)

    primaryStage.setOnCloseRequest(_ => statisticsCollector.actorSystem.terminate())
    primaryStage.show()
  }
}
