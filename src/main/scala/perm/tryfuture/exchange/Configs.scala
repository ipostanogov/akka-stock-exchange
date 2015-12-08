package perm.tryfuture.exchange

import com.typesafe.config.ConfigFactory

object Configs {
  private val root = ConfigFactory.load()
  val systemExchangeServer = root.getConfig("systemExchangeServer")
  val systemNewsServers = root.getConfig("systemNewsServers")
  val systemTraders = root.getConfig("systemTraders")
  val systemUi = root.getConfig("systemUi")
  val numberOfNewsServers = 3
}