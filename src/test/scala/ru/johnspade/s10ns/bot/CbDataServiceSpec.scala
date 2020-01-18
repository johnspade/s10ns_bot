package ru.johnspade.s10ns.bot

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.subscription.tags.{PageNumber, SubscriptionId}

class CbDataServiceSpec extends AnyFlatSpec with Matchers {
  "decode" should "decode CSV as a case class" in {
    new CbDataService[IO]().decode("S10n\u001D17\u001D2").unsafeRunSync shouldBe S10n(SubscriptionId(17L), PageNumber(2))
  }
}
