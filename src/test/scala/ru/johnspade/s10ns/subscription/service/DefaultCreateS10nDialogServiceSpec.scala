package ru.johnspade.s10ns.subscription.service

import cats.Id
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._

import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.PartialFunctionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.KeyboardButton
import telegramium.bots.ReplyKeyboardMarkup
import telegramium.bots.ReplyKeyboardRemove

import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.CreateS10nDialog
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.bot.NameTooLong
import ru.johnspade.s10ns.bot.NotANumber
import ru.johnspade.s10ns.bot.NumberMustBePositive
import ru.johnspade.s10ns.bot.TextCannotBeEmpty
import ru.johnspade.s10ns.bot.UnknownCurrency
import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogState
import ru.johnspade.s10ns.subscription.dialog.CreateS10nMsgService
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.impl.DefaultCreateS10nDialogFsmService
import ru.johnspade.s10ns.subscription.service.impl.DefaultCreateS10nDialogService
import ru.johnspade.s10ns.user.InMemoryUserRepository
import ru.johnspade.s10ns.user.User

class DefaultCreateS10nDialogServiceSpec
    extends AnyFlatSpec
    with Matchers
    with DiffShouldMatcher
    with PartialFunctionValues
    with MockFactory {

  private val userRepo     = new InMemoryUserRepository
  private val mockS10nRepo = mock[SubscriptionRepository[Id]]
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepo)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](
    moneyService,
    new S10nInfoService[IO],
    new S10nsListReplyMessageService
  )
  private val calendarService      = new CalendarService
  private val createS10nMsgService = new CreateS10nMsgService[IO](calendarService)
  private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, Id](
    mockS10nRepo,
    userRepo,
    dialogEngine,
    s10nsListMessageService,
    createS10nMsgService
  )
  private val createS10nDialogService = new DefaultCreateS10nDialogService[IO, Id](
    createS10nDialogFsmService,
    createS10nMsgService,
    dialogEngine
  )

  private val user  = User(0L, "John", None)
  private val draft = SubscriptionDraft.create(0L)

  "onCreateCommand" should "ask for a subscription's currency" in {
    val result = createS10nDialogService.onCreateCommand(user).unsafeRunSync()
    result shouldMatchTo {
      List(
        ReplyMessage(
          "Please select a popular currency from the options below, or manually type in the code for any other currency not listed in the buttons. For example: 'CNY' for Chinese Yuan or 'INR' for Indian Rupee:",
          markup = ReplyKeyboardMarkup(
            keyboard = List(
              CurrencyUnit.EUR,
              CurrencyUnit.GBP,
              CurrencyUnit.AUD,
              CurrencyUnit.of("NZD"),
              CurrencyUnit.USD,
              CurrencyUnit.CAD,
              CurrencyUnit.CHF,
              CurrencyUnit.JPY,
              CurrencyUnit.of("RUB")
            )
              .map(currency => KeyboardButton(currency.getCode))
              .grouped(5)
              .toList,
            oneTimeKeyboard = Some(true),
            resizeKeyboard = Some(true)
          ).some
        )
      )
    }
  }

  "onCreateWithDefaultCurrencyCommand" should "ask for a subscription's name" in {
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user).unsafeRunSync() shouldMatchTo {
      List(ReplyMessage("Name:", ReplyKeyboardRemove(removeKeyboard = true).some))
    }
  }

  behavior of "saveDraft"

  it should "fail if a text is missing" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    createS10nDialogService.saveDraft.valueAt(user, dialog, None).unsafeRunSync() shouldBe
      TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a name is too long" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    val name   = List.fill(257)("a").mkString
    createS10nDialogService.saveDraft.valueAt(user, dialog, name.some).unsafeRunSync() shouldBe
      NameTooLong.invalidNec[String]
  }

  it should "fail if a currency's code is unknown" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Currency, draft)
    val code   = "AAA"
    createS10nDialogService.saveDraft.valueAt(user, dialog, code.some).unsafeRunSync() shouldBe
      UnknownCurrency.invalidNec[CurrencyUnit]
  }

  it should "fail if an amount is not a number" in {
    val dialog     = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    val amountText = "NaN"
    createS10nDialogService.saveDraft.valueAt(user, dialog, amountText.some).unsafeRunSync() shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if an amount is not positive" in {
    val dialog     = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    val amountText = "-3"
    createS10nDialogService.saveDraft.valueAt(user, dialog, amountText.some).unsafeRunSync() shouldBe
      NumberMustBePositive.invalidNec[BigDecimal]
  }

  it should "fail if a duration is not a number" in {
    val dialog       = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    val durationText = "NaN"
    createS10nDialogService.saveDraft.valueAt(user, dialog, durationText.some).unsafeRunSync() shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if a duration is not positive" in {
    val dialog       = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    val durationText = "-1"
    createS10nDialogService.saveDraft.valueAt(user, dialog, durationText.some).unsafeRunSync() shouldBe
      NumberMustBePositive.invalidNec[BigDecimal]
  }
}
