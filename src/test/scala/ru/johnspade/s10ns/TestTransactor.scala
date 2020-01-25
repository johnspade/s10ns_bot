package ru.johnspade.s10ns

import cats.Id
import cats.arrow.FunctionK
import cats.effect.IO

object TestTransactor {
  implicit val transact: FunctionK[Id, IO] = new FunctionK[Id, IO] {
    override def apply[A](fa: Id[A]): IO[A] = IO(fa)
  }
}
