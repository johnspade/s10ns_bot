package ru.johnspade.s10ns

import cats.effect.IO

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tofu.logging.Logs

trait SpecBase extends AnyFlatSpec with Matchers with OptionValues {
  implicit val logs: Logs[IO, IO] = Logs.sync[IO, IO]
}
