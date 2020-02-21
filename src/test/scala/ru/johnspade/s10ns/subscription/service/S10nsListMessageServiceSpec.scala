package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}

import cats.effect.{Clock, IO}
import cats.syntax.option._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{EditS10n, EditS10nAmount, EditS10nBillingPeriod, EditS10nCurrency, EditS10nFirstPaymentDate, EditS10nName, EditS10nOneTime, MoneyService, RemoveS10n, S10n, S10ns}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit, Subscription}
import ru.johnspade.s10ns.user.tags.UserId
import telegramium.bots.{InlineKeyboardMarkup, KeyboardMarkup, MarkupInlineKeyboard}

import scala.concurrent.ExecutionContext

class S10nsListMessageServiceSpec extends AnyFlatSpec with Matchers with OptionValues with DiffMatcher {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock

  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))

  private val firstPaymentDate = LocalDate.now(ZoneOffset.UTC).minusDays(35)
  private val s10n1 = Subscription(
    SubscriptionId(1L),
    UserId(0L),
    SubscriptionName("Netflix"),
    Money.of(CurrencyUnit.USD, 13.37),
    OneTimeSubscription(false).some,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month).some,
    FirstPaymentDate(firstPaymentDate).some
  )
  private val s10n2 = Subscription(
    SubscriptionId(2L),
    UserId(0L),
    SubscriptionName("Spotify"),
    Money.of(CurrencyUnit.EUR, 5.3),
    OneTimeSubscription(false).some,
    BillingPeriod(BillingPeriodDuration(1), BillingPeriodUnit.Month).some,
    FirstPaymentDate(LocalDate.of(2020, 1, 1)).some
  )

  behavior of "createSubscriptionsPage"

  it should "generate a message without nav buttons if an user has less than 10 subscriptions" in {
    val page = s10nsListMessageService.createSubscriptionsPage(List(s10n1, s10n2), PageNumber(0), CurrencyUnit.EUR)
      .unsafeRunSync
    page.text shouldBe
      """|17.27 €
         |
         |1. Netflix – 13.37 $
         |2. Spotify – 5.30 €""".stripMargin
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

  it should "generate a message with navigation buttons for next and previous pages" in {
    val page = s10nsListMessageService.createSubscriptionsPage(createS10ns(21), PageNumber(1), CurrencyUnit.EUR)
      .unsafeRunSync
    page.text shouldBe
      s"""|0.00 €
          |
          |${List.tabulate(10)(n => s"${n + 1}. s10n${n + 10} – 1.00 €").mkString("\n")}""".stripMargin
    page.markup.value should matchTo[KeyboardMarkup] {
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List.tabulate(5)(n => inlineKeyboardButton((n + 1).toString, S10n(SubscriptionId(n + 10.toLong), PageNumber(1)))),
        List.tabulate(5)(n => inlineKeyboardButton((n + 6).toString, S10n(SubscriptionId(n + 15.toLong), PageNumber(1)))),
        List(
          inlineKeyboardButton("⬅", S10ns(PageNumber(0))),
          inlineKeyboardButton("➡", S10ns(PageNumber(2)))
        )
      )))
    }
  }

  it should "generate a message with the previous button only if there is no next page" in {
    val page = s10nsListMessageService.createSubscriptionsPage(createS10ns(11), PageNumber(1), CurrencyUnit.EUR)
      .unsafeRunSync
    page.text shouldBe
      s"""|0.00 €
          |
          |1. s10n10 – 1.00 €""".stripMargin
    page.markup.value should matchTo[KeyboardMarkup] {
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("1", S10n(SubscriptionId(10L), PageNumber(1)))),
        List(inlineKeyboardButton("⬅", S10ns(PageNumber(0))))
      )))
    }
  }

  it should "generate a message with the next button only if there is no previous page" in {
    val page = s10nsListMessageService.createSubscriptionsPage(createS10ns(11), PageNumber(0), CurrencyUnit.EUR)
      .unsafeRunSync
    page.text shouldBe
      s"""|0.00 €
          |
          |${List.tabulate(10)(n => s"${n + 1}. s10n$n – 1.00 €").mkString("\n")}""".stripMargin
    page.markup.value should matchTo[KeyboardMarkup] {
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List.tabulate(5)(n => inlineKeyboardButton((n + 1).toString, S10n(SubscriptionId(n.toLong), PageNumber(0)))),
        List.tabulate(5)(n => inlineKeyboardButton((n + 6).toString, S10n(SubscriptionId(n + 5.toLong), PageNumber(0)))),
        List(inlineKeyboardButton("➡", S10ns(PageNumber(1))))
      )))
    }
  }

  behavior of "createSubscriptionMessage"

  it should "generate a message with subscription's info" in {
    val page = PageNumber(0)
    val message = s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, s10n1, page).unsafeRunSync
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
      SubscriptionId(0L),
      UserId(0L),
      SubscriptionName("s10n"),
      Money.of(CurrencyUnit.EUR, 1),
      None,
      None,
      None
    )
    val page = PageNumber(0)
    val message = s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, subscription, page).unsafeRunSync
    message.text shouldBe
      s"""|*s10n*
          |
          |1.00 €
          |""".stripMargin
    checkS10nMessageMarkup(message.markup, subscription.id, page)
  }

  it should "generate a correct message for one time subscriptions" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).minusMonths(1))
    val subscription = Subscription(
      SubscriptionId(0L),
      UserId(0L),
      SubscriptionName("s10n"),
      Money.of(CurrencyUnit.EUR, 1),
      OneTimeSubscription(true).some,
      None,
      firstPaymentDate.some
    )
    val page = PageNumber(0)
    val message = s10nsListMessageService.createSubscriptionMessage(CurrencyUnit.EUR, subscription, page).unsafeRunSync
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

    val markup = s10nsListMessageService.createEditS10nMarkup(s10n1, PageNumber(0))
    markup should matchTo {
      InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("Name", EditS10nName(id))),
        List(inlineKeyboardButton("Amount", EditS10nAmount(id))),
        List(inlineKeyboardButton("Currency/amount", EditS10nCurrency(id))),
        List(inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id))),
        List(inlineKeyboardButton("Billing period", EditS10nBillingPeriod(id))),
        List(inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id))),
        List(inlineKeyboardButton("Back", S10n(id, PageNumber(0))))
      ))
    }
  }

  it should "generate a markup for one time subscription editing" in {
    val firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC).minusMonths(1))
    val subscription = Subscription(
      SubscriptionId(0L),
      UserId(0L),
      SubscriptionName("s10n"),
      Money.of(CurrencyUnit.EUR, 1),
      OneTimeSubscription(true).some,
      None,
      firstPaymentDate.some
    )

    import subscription.id

    val markup = s10nsListMessageService.createEditS10nMarkup(subscription, PageNumber(0))
    markup should matchTo {
      InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("Name", EditS10nName(id))),
        List(inlineKeyboardButton("Amount", EditS10nAmount(id))),
        List(inlineKeyboardButton("Currency/amount", EditS10nCurrency(id))),
        List(inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id))),
        List(),
        List(inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id))),
        List(inlineKeyboardButton("Back", S10n(id, PageNumber(0))))
      ))
    }
  }

  private def createS10ns(count: Int): List[Subscription] =
    List.tabulate(count) { n =>
      Subscription(
        SubscriptionId(n.toLong),
        UserId(0L),
        SubscriptionName(s"s10n$n"),
        Money.of(CurrencyUnit.EUR, 1),
        None,
        None,
        None
      )
    }

  private def checkS10nMessageMarkup(markup: Option[KeyboardMarkup], s10nId: SubscriptionId, page: PageNumber): Unit =
    markup.value should matchTo[KeyboardMarkup] {
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("Edit", EditS10n(s10nId, page))),
        List(inlineKeyboardButton("Remove", RemoveS10n(s10nId, page))),
        List(inlineKeyboardButton("List", S10ns(page)))
      )))
    }
}
