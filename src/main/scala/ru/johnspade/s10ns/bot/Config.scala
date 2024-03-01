package ru.johnspade.s10ns.bot

import java.net.URI

import pureconfig.ConfigReader
import pureconfig.module.magnolia.semiauto.reader.deriveReader

final case class DbConfig(driver: String, url: String, user: String, password: String)
object DbConfig {
  implicit val dbConfigReader: ConfigReader[DbConfig] = ConfigReader.fromCursor[DbConfig] { config =>
    for {
      obj <- config.asObjectCursor
      fluent = obj.fluent
      driver <- fluent.at("driver").asString
      url <- fluent.at("url").asString
      dbUri = new URI(url)
      userInfo = dbUri.getUserInfo.split(":")
    } yield DbConfig(
      driver,
      s"jdbc:postgresql://${dbUri.getHost}:${dbUri.getPort}${dbUri.getPath}",
      user = userInfo(0),
      password = userInfo(1)
    )
  }
}

case class BotConfig(token: String, port: Int, url: String, host: String)

object BotConfig {
  implicit val botConfigReader: ConfigReader[BotConfig] = deriveReader[BotConfig]
}

case class FixerConfig(token: String)

object FixerConfig {
  implicit val fixerConfigReader: ConfigReader[FixerConfig] = deriveReader[FixerConfig]
}

case class Config(db: DbConfig, bot: BotConfig, fixer: FixerConfig)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}
