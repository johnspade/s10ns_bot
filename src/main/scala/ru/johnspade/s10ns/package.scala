package ru.johnspade

import java.time.Instant

import cats.{Functor, Monad, MonadError}
import cats.effect.{Clock, Timer}
import cats.implicits._
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
}
