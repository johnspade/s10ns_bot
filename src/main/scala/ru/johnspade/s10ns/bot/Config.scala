package ru.johnspade.s10ns.bot

import pureconfig.ConfigReader
import pureconfig.module.magnolia.semiauto.reader.deriveReader

case class AppConfig(db: String)

object AppConfig {
  implicit val appConfigReader: ConfigReader[AppConfig] = deriveReader[AppConfig]
}

case class BotConfig(token: String, port: Int, url: String, host: String)

object BotConfig {
  implicit val botConfigReader: ConfigReader[BotConfig] = deriveReader[BotConfig]
}

case class FixerConfig(token: String)

object FixerConfig {
  implicit val fixerConfigReader: ConfigReader[FixerConfig] = deriveReader[FixerConfig]
}

case class Config(app: AppConfig, bot: BotConfig, fixer: FixerConfig)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}
