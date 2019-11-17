package ru.johnspade.s10ns.common

case class AppConfig(db: String)

case class TelegramConfig(token: String)

case class FixerConfig(token: String)

case class Config(app: AppConfig, telegram: TelegramConfig, fixer: FixerConfig)
