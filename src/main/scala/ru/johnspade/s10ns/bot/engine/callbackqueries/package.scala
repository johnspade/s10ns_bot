package ru.johnspade.s10ns.bot.engine

import cats.data.{EitherT, Kleisli, OptionT}
import cats.implicits._
import cats.{Applicative, Defer, Monad}
import telegramium.bots.CallbackQuery

package object callbackqueries {
  type DecodeResult[F[_], I] = EitherT[F, DecodeFailure, I]

  type CallbackQueries[I, F[_]] = Kleisli[F, CallbackQueryData[I], Unit]

  type CallbackQueryRoutes[I, F[_]] = CallbackQueries[I, OptionT[F, *]]

  type CallbackQueryContextRoutes[I, A, F[_]] = Kleisli[OptionT[F, *], ContextCallbackQuery[I, A], Unit]

  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]

  type CallbackQueryContextMiddleware[I, A, F[_]] = Middleware[OptionT[F, *], ContextCallbackQuery[I, A], Unit, CallbackQueryData[I], Unit]

  object CallbackQueryRoutes {
    def of[I, F[_]: Defer: Applicative](pf: PartialFunction[CallbackQueryData[I], F[Unit]]): CallbackQueryRoutes[I, F] =
      Kleisli(input => OptionT(Defer[F].defer(pf.lift(input).sequence)))
  }

  object CallbackQueryContextRoutes {
    def of[I, A, F[_]: Defer: Applicative](pf: PartialFunction[ContextCallbackQuery[I, A], F[Unit]]): CallbackQueryContextRoutes[I, A, F] =
      Kleisli(cb => OptionT(Defer[F].defer(pf.lift(cb).sequence)))
  }

  object CallbackQueryContextMiddleware {
    def apply[F[_]: Monad, I, A](
      retrieveContext: Kleisli[OptionT[F, *], CallbackQuery, A]
    ): CallbackQueryContextMiddleware[I, A, F] =
      _.compose(Kleisli((cb: CallbackQueryData[I]) => retrieveContext(cb.cb).map(ContextCallbackQuery(_, cb))))
  }
}
