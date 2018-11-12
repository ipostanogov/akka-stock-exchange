package perm.tryfuture.exchange

import com.typesafe.config.{Config, ConfigFactory}

object Configs {
  lazy val systemExchangeServer: Config = root.getConfig("systemExchangeServer")
  lazy val systemNewsServers: Config = root.getConfig("systemNewsServers")
  lazy val systemTraders: Config = root.getConfig("systemTraders")
  lazy val systemUi: Config = root.getConfig("systemUi")
  lazy val actorSystemName: String = root.getString("actor-system-name")
  lazy val numberOfNewsServers: Int = systemNewsServers.getInt("number-of-servers")
  private val root = ConfigFactory.load().resolve()
}
