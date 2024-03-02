package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option._

import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.KeyboardMarkup
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.EditS10nAmount
import ru.johnspade.s10ns.bot.EditS10nBillingPeriod
import ru.johnspade.s10ns.bot.EditS10nCurrency
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDate
import ru.johnspade.s10ns.bot.EditS10nName
import ru.johnspade.s10ns.bot.EditS10nOneTime
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.S10nsPeriod
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.Subscription

class S10nsListMessageServiceSpec extends AnyFlatSpec with Matchers with OptionValues with DiffShouldMatcher {

  private val moneyService    = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nInfoService = new S10nInfoService[IO]
  private val s10nsListMessageService =
    new S10nsListMessageService[IO](moneyService, s10nInfoService, new S10nsListReplyMessageService)

  private val today                = LocalDate.now(ZoneOffset.UTC)
  private val firstPaymentDate     = today.minusDays(35)
  private val daysUntilNextPayment = Period.between(today, firstPaymentDate.plusMonths(2)).getDays
  private val s10n1 = Subscription(
    1L,
    0L,
    "Netflix",
    Money.of(CurrencyUnit.USD, 13.37),
    false.some,
    BillingPeriod(1, BillingPeriodUnit.Month).some,
    firstPaymentDate.some
  )
  private val s10n2 = Subscription(
    2L,
    0L,
    "Spotify",
    Money.of(CurrencyUnit.EUR, 5.3),
    false.some,
    BillingPeriod(1, BillingPeriodUnit.Month).some,
    None
  )

  behavior of "createSubscriptionsPage"

  it should "generate a message without nav buttons if an user has less than 10 subscriptions" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(List(s10n1, s10n2), 0, CurrencyUnit.EUR)
      .unsafeRunSync()
    page.text shouldBe
      s"""|Monthly: 17.27 €
          |
          |1. Spotify – 5.30 €
          |2. Netflix – ≈11.97 € <b>[${daysUntilNextPayment}d]</b>""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List(
            inlineKeyboardButton("1", S10n(2L, 0)),
            inlineKeyboardButton("2", S10n(1L, 0))
          ),
          List(),
          List(
            inlineKeyboardButton("Yearly", S10nsPeriod(BillingPeriodUnit.Year, 0)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  it should "generate a message with navigation buttons for next and previous pages" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(createS10ns(21), 1, CurrencyUnit.EUR)
      .unsafeRunSync()
    page.text shouldBe
      s"""|Monthly: 0.00 €
          |
          |${List.tabulate(10)(n => s"${n + 1}. s10n${n + 10} – 1.00 €").mkString("\n")}""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List.tabulate(5)(n => inlineKeyboardButton((n + 1).toString, S10n(n + 10.toLong, 1))),
          List.tabulate(5)(n => inlineKeyboardButton((n + 6).toString, S10n(n + 15.toLong, 1))),
          List(
            inlineKeyboardButton("⬅", S10ns(0)),
            inlineKeyboardButton("➡", S10ns(2))
          ),
          List(
            inlineKeyboardButton("Yearly", S10nsPeriod(BillingPeriodUnit.Year, 1)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  it should "generate a message with the previous button only if there is no next page" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(createS10ns(11), 1, CurrencyUnit.EUR)
      .unsafeRunSync()
    page.text shouldBe
      s"""|Monthly: 0.00 €
          |
          |1. s10n10 – 1.00 €""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List(inlineKeyboardButton("1", S10n(10L, 1))),
          List(inlineKeyboardButton("⬅", S10ns(0))),
          List(
            inlineKeyboardButton("Yearly", S10nsPeriod(BillingPeriodUnit.Year, 1)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  it should "generate a message with the next button only if there is no previous page" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(createS10ns(11), 0, CurrencyUnit.EUR)
      .unsafeRunSync()
    page.text shouldBe
      s"""|Monthly: 0.00 €
          |
          |${List.tabulate(10)(n => s"${n + 1}. s10n$n – 1.00 €").mkString("\n")}""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List.tabulate(5)(n => inlineKeyboardButton((n + 1).toString, S10n(n.toLong, 0))),
          List.tabulate(5)(n => inlineKeyboardButton((n + 6).toString, S10n(n + 5.toLong, 0))),
          List(inlineKeyboardButton("➡", S10ns(1))),
          List(
            inlineKeyboardButton("Yearly", S10nsPeriod(BillingPeriodUnit.Year, 0)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  it should "generate a message with the 'Weekly' button if a 'yearly' period is selected" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(
        List(s10n1),
        0,
        CurrencyUnit.EUR,
        BillingPeriodUnit.Year
      )
      .unsafeRunSync()
    page.text shouldBe
      s"""|Yearly: 143.67 €
          |
          |1. Netflix – ≈143.67 € <b>[${daysUntilNextPayment}d]</b>""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List(inlineKeyboardButton("1", S10n(1L, 0))),
          List(),
          List(
            inlineKeyboardButton("Weekly", S10nsPeriod(BillingPeriodUnit.Week, 0)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  it should "generate a message with the 'Monthly' button if a 'Weekly' period is selected" in {
    val page = s10nsListMessageService
      .createSubscriptionsPage(
        List(s10n1),
        0,
        CurrencyUnit.EUR,
        BillingPeriodUnit.Week
      )
      .unsafeRunSync()
    page.text shouldBe
      s"""|Weekly: 2.75 €
          |
          |1. Netflix – ≈2.75 € <b>[${daysUntilNextPayment}d]</b>""".stripMargin
    page.markup.value shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List(inlineKeyboardButton("1", S10n(1L, 0))),
          List(),
          List(
            inlineKeyboardButton("Monthly", S10nsPeriod(BillingPeriodUnit.Month, 0)),
            InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")
          )
        )
      )
    }
  }

  behavior of "createSubscriptionMessage"

  it should "generate a message with subscription's info" in {
    val page    = 0
    val message = s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, s10n1, page).unsafeRunSync()
    val expectedNextPayment = firstPaymentDate.plusMonths(2)
    message.text shouldBe
      s"""|*Netflix*
          |
          |13.37 $$
          |≈11.97 €
          |
          |_Billing period:_ every 1 month
          |_Next payment:_ $expectedNextPayment
          |_First payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate)}
          |_Paid in total:_ 26.74 $$""".stripMargin
    checkS10nMessageMarkup(message.markup, s10n1.id, page)
  }

  it should "generate a message without missing optional fields" in {
    val subscription = Subscription(
      0L,
      0L,
      "s10n",
      Money.of(CurrencyUnit.EUR, 1),
      None,
      None,
      None
    )
    val page = 0
    val message =
      s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, subscription, page).unsafeRunSync()
    message.text shouldBe
      s"""|*s10n*
          |
          |1.00 €
          |""".stripMargin
    checkS10nMessageMarkup(message.markup, subscription.id, page)
  }

  it should "generate a correct message for one time subscriptions" in {
    val firstPaymentDate = LocalDate.now(ZoneOffset.UTC).minusMonths(1)
    val subscription = Subscription(
      0L,
      0L,
      "s10n",
      Money.of(CurrencyUnit.EUR, 1),
      true.some,
      None,
      firstPaymentDate.some
    )
    val page = 0
    val message =
      s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, subscription, page).unsafeRunSync()
    message.text shouldBe
      s"""|*s10n*
          |
          |1.00 €
          |
          |_First payment:_ ${DateTimeFormatter.ISO_DATE.format(firstPaymentDate)}""".stripMargin
    checkS10nMessageMarkup(message.markup, subscription.id, page)
  }

  behavior of "createEditS10nMarkup"

  it should "generate a markup for subscription editing" in {
    import s10n1.id

    val markup = s10nsListMessageService.createEditS10nMarkup(s10n1, 0)
    markup shouldMatchTo {
      InlineKeyboardMarkups.singleColumn(
        List(
          inlineKeyboardButton("Name", EditS10nName(id)),
          inlineKeyboardButton("Amount", EditS10nAmount(id)),
          inlineKeyboardButton("Currency/amount", EditS10nCurrency(id)),
          inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id)),
          inlineKeyboardButton("Billing period", EditS10nBillingPeriod(id)),
          inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id)),
          inlineKeyboardButton("Back", S10n(id, 0))
        )
      )
    }
  }

  it should "generate a markup for one time subscription editing" in {
    val firstPaymentDate = LocalDate.now(ZoneOffset.UTC).minusMonths(1)
    val subscription = Subscription(
      0L,
      0L,
      "s10n",
      Money.of(CurrencyUnit.EUR, 1),
      true.some,
      None,
      firstPaymentDate.some
    )

    import subscription.id

    val markup = s10nsListMessageService.createEditS10nMarkup(subscription, 0)
    markup shouldMatchTo {
      InlineKeyboardMarkup(
        List(
          List(inlineKeyboardButton("Name", EditS10nName(id))),
          List(inlineKeyboardButton("Amount", EditS10nAmount(id))),
          List(inlineKeyboardButton("Currency/amount", EditS10nCurrency(id))),
          List(inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id))),
          List(),
          List(inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id))),
          List(inlineKeyboardButton("Back", S10n(id, 0)))
        )
      )
    }
  }

  private def createS10ns(count: Int): List[Subscription] =
    List.tabulate(count) { n =>
      Subscription(
        n.toLong,
        0L,
        s"s10n$n",
        Money.of(CurrencyUnit.EUR, 1),
        None,
        None,
        None
      )
    }

  private def checkS10nMessageMarkup(markup: Option[KeyboardMarkup], s10nId: Long, page: Int): Unit =
    markup.value shouldMatchTo {
      InlineKeyboardMarkups.singleColumn(
        List(
          inlineKeyboardButton("Edit", EditS10n(s10nId, page)),
          inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, page)),
          inlineKeyboardButton("Remove", RemoveS10n(s10nId, page)),
          inlineKeyboardButton("List", S10ns(page))
        )
      )
    }
}
