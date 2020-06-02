package ru.johnspade.s10ns

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tofu.logging.Logs

import scala.concurrent.ExecutionContext

trait SpecBase extends AnyFlatSpec with Matchers with OptionValues {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val logs: Logs[IO, IO] = Logs.sync[IO, IO]
}
