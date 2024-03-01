package ru.johnspade

import java.time.Instant
import scala.concurrent.duration._

import cats.Functor
import cats.Monad
import cats.MonadError
import cats.effect.Clock
import cats.effect.Temporal
import cats.implicits._

import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.high.Api
import tofu.logging.Logging

import ru.johnspade.s10ns.bot.CbData
import ru.johnspade.s10ns.bot.Errors
import ru.johnspade.s10ns.bot.engine.TelegramOps.ackCb
import ru.johnspade.s10ns.user.User

package object s10ns {
  def repeat[F[_]: Monad: Temporal: Logging](f: F[Unit], duration: FiniteDuration)(implicit
      monadError: MonadError[F, Throwable]
  ): F[Unit] = {
    val FWithErrorHandling = f.handleErrorWith(e => Logging[F].errorCause(e.getMessage, e))

    FWithErrorHandling >> Temporal[F].sleep(duration) >> repeat(FWithErrorHandling, duration)
  }

  def currentTimestamp[F[_]: Clock](implicit F: Functor[F]): F[Instant] =
    Clock[F].realTime.map(realTime => Instant.ofEpochMilli(realTime.toMillis))

  type CbDataRoutes[F[_]] = CallbackQueryRoutes[CbData, Unit, F]

  type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, Unit, F]

  def ackDefaultError[F[_]](cb: CallbackQuery)(implicit bot: Api[F], F: Functor[F]): F[Unit] =
    ackCb(cb, Errors.Default.some)
}
