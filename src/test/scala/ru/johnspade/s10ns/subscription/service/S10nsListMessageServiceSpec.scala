package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.effect.{Clock, IO}
import cats.syntax.option._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{MoneyService, S10n}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, BillingPeriodUnit, FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriod, Subscription}
import ru.johnspade.s10ns.user.tags.UserId
import telegramium.bots.{InlineKeyboardMarkup, KeyboardMarkup, MarkupInlineKeyboard}

import scala.concurrent.ExecutionContext

class S10nsListMessageServiceSpec extends AnyFlatSpec with Matchers with OptionValues with DiffMatcher {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock

  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))

  private val s10n1 = Subscription(
    SubscriptionId(1L),
    UserId(0L),
    SubscriptionName("Netflix"),
    Money.of(CurrencyUnit.USD, 13.37),
    OneTimeSubscription(false).some,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.MONTHS)).some,
    FirstPaymentDate(LocalDate.of(2020, 2, 1)).some
  )
  private val s10n2 = Subscription(
    SubscriptionId(2L),
    UserId(0L),
    SubscriptionName("Spotify"),
    Money.of(CurrencyUnit.EUR, 5.3),
    OneTimeSubscription(false).some,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit(ChronoUnit.MONTHS)).some,
    FirstPaymentDate(LocalDate.of(2020, 1, 1)).some
  )

  "createSubscriptionsPage" should "generate a message with a list of subscriptions" in {
    val page = s10nsListMessageService.createSubscriptionsPage(List(s10n1, s10n2), PageNumber(0), CurrencyUnit.EUR)
      .unsafeRunSync
    page.text shouldBe
      """|17.27 EUR
         |
         |1. Netflix – 13.37 $
         |2. Spotify – 5.30 EUR""".stripMargin
    page.markup.value should matchTo[KeyboardMarkup] {
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
          List(
            inlineKeyboardButton("1", S10n(SubscriptionId(1L), PageNumber(0))),
            inlineKeyboardButton("2", S10n(SubscriptionId(2L), PageNumber(0)))
          ),
          List()
        )))
    }
  }
}
