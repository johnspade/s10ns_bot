package ru.johnspade

import java.time.Instant

import cats.effect.{Clock, Sync, Timer}
import cats.implicits._
import cats.{Functor, Monad, MonadError}
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.bot.{CbData, Errors}
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.s10ns.user.User
import telegramium.bots.CallbackQuery
import telegramium.bots.high.Api
import tofu.logging.Logging

import scala.concurrent.duration._

package object s10ns {
  def repeat[F[_]: Monad: Timer: Logging](f: F[Unit], duration: FiniteDuration)(
    implicit monadError: MonadError[F, Throwable]
  ): F[Unit] = {
    val FWithErrorHandling = f.handleErrorWith(e => Logging[F].errorCause(e.getMessage, e))

    FWithErrorHandling >> Timer[F].sleep(duration) >> repeat(FWithErrorHandling, duration)
  }

  def currentTimestamp[F[_]: Clock: Functor]: F[Instant] = Clock[F].realTime(MILLISECONDS).map(Instant.ofEpochMilli)

  type CbDataRoutes[F[_]] = CallbackQueryRoutes[CbData, Unit, F]

  type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, Unit, F]

  def ackDefaultError[F[_]: Sync](cb: CallbackQuery)(implicit bot: Api[F]): F[Unit] = ackCb[F](cb, Errors.Default.some)
}
