package ru.johnspade.s10ns.bot.engine.callbackqueries

import cats.data.OptionT
import cats.{Monad, MonadError}
import telegramium.bots.CallbackQuery

object CallbackQueryHandler {
  def handle[F[_]: Monad, I](
    cb: CallbackQuery,
    routes: CallbackQueryRoutes[I, F],
    decoder: CallbackDataDecoder[F, I],
    onNotFound: CallbackQuery => F[Unit]
  )(implicit F: MonadError[F, Throwable]): F[Unit] =
    (for {
      queryData <- OptionT.fromOption[F](cb.data)
      data <- OptionT.liftF(F.rethrow(decoder.decode(queryData).value))
      res <- routes.run(CallbackQueryData(data, cb))
    } yield res)
      .getOrElse(onNotFound(cb))
}
