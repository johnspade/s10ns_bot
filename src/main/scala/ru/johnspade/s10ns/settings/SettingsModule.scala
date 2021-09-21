package ru.johnspade.s10ns.settings

import cats.Defer
import cats.implicits._
import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.bot.BotModule
import ru.johnspade.s10ns.bot.engine.DefaultMsgService
import telegramium.bots.high.Api
import tofu.logging.Logs
import cats.effect.Temporal

final class SettingsModule[F[_]] private(
  val settingsController: SettingsController[F]
)

object SettingsModule {
  def make[F[_]: Temporal: Defer](botModule: BotModule[F, ConnectionIO])(
    implicit bot: Api[F], logs: Logs[F, F]
  ): F[SettingsModule[F]] = {
    val settingsMsgService = new DefaultMsgService[F, SettingsDialogState]
    val settingsService = new DefaultSettingsService[F](botModule.dialogEngine, settingsMsgService)
    SettingsController(settingsService).map { settingsController =>
      new SettingsModule[F](
        settingsController = settingsController
      )
    }
  }
}
