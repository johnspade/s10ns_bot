package ru.johnspade.s10ns.bot

import cats.effect.IO
import cats.syntax.either._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CbDataServiceSpec extends AnyFlatSpec with Matchers {
  "decode" should "decode CSV as a case class" in {
    new CbDataService[IO]().decode("S10n\u001D17\u001D2").valueOr(throw _) shouldBe S10n(
      17L,
      2
    )
  }
}
