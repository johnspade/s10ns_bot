package ru.johnspade.s10ns.settings

import cats.Defer
import cats.effect.Temporal
import cats.implicits._
import cats.~>

import doobie.free.connection.ConnectionIO
import telegramium.bots.high.Api
import tofu.logging.Logs

import ru.johnspade.s10ns.bot.BotModule
import ru.johnspade.s10ns.bot.engine.DefaultMsgService
import ru.johnspade.s10ns.user.UserModule

final class SettingsModule[F[_], D[_]] private (
    val settingsController: SettingsController[F]
)

object SettingsModule {
  def make[F[_]: Temporal: Defer](botModule: BotModule[F, ConnectionIO], userModule: UserModule[ConnectionIO])(implicit
      bot: Api[F],
      logs: Logs[F, F],
      transact: ConnectionIO ~> F
  ): F[SettingsModule[F, ConnectionIO]] = {
    val settingsMsgService = new DefaultMsgService[F, SettingsDialogState]
    val settingsService =
      new DefaultSettingsService[F, ConnectionIO](botModule.dialogEngine, settingsMsgService, userModule.userRepository)
    SettingsController(settingsService).map { settingsController =>
      new SettingsModule[F, ConnectionIO](
        settingsController = settingsController
      )
    }
  }
}
