package ru.johnspade

import cats.{Monad, MonadError}
import cats.effect.Timer
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
}
