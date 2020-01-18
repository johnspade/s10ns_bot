package ru.johnspade.s10ns.bot

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.subscription.tags.{PageNumber, SubscriptionId}

class CbDataSpec extends AnyFlatSpec with Matchers {
  "toCsv" should "encode a case class to CSV with discriminator" in {
    S10n(SubscriptionId(17L), PageNumber(2)).toCsv shouldBe "S10n\u001D17\u001D2"
  }
}
