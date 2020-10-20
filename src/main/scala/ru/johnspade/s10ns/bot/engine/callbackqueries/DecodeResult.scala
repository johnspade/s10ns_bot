package ru.johnspade.s10ns.bot.engine.callbackqueries

import cats.data.EitherT
import cats.implicits._
import cats.{Applicative, Functor}

object DecodeResult {
  def apply[F[_], A](fa: F[Either[DecodeFailure, A]]): DecodeResult[F, A] =
    EitherT(fa)

  def success[F[_], A](fa: F[A])(implicit F: Functor[F]): DecodeResult[F, A] =
    EitherT(fa.map(Right(_)))

  def success[F[_], A](a: A)(implicit F: Applicative[F]): DecodeResult[F, A] =
    EitherT(F.pure(Right(a)))

  def failure[F[_], A](fe: F[DecodeFailure])(implicit F: Functor[F]): DecodeResult[F, A] =
    EitherT(fe.map(Left(_)))

  def failure[F[_], A](e: DecodeFailure)(implicit F: Applicative[F]): DecodeResult[F, A] =
    EitherT(F.pure(Left(e)))
}
