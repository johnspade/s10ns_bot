package ru.johnspade.s10ns.settings

import cats.effect.{Sync, Timer}
import cats.implicits._
import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.bot.BotModule
import ru.johnspade.s10ns.bot.engine.DefaultMsgService
import tofu.logging.Logs

final class SettingsModule[F[_]] private(
  val settingsController: SettingsController[F]
)

object SettingsModule {
  def make[F[_]: Sync: Timer](botModule: BotModule[F, ConnectionIO])(
    implicit logs: Logs[F, F]
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
