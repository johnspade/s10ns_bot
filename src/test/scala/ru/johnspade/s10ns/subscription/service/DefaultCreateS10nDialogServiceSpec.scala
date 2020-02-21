package ru.johnspade.s10ns.subscription.service

import cats.Id
import cats.effect.{Clock, IO}
import cats.implicits._
import com.softwaremill.diffx.scalatest.DiffMatcher
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.PartialFunctionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, ReplyMessage}
import ru.johnspade.s10ns.bot.{CreateS10nDialog, MoneyService, NameTooLong, NotANumber, NumberMustBePositive, StateMessageService, TextCannotBeEmpty, UnknownCurrency}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import telegramium.bots.{KeyboardButton, MarkupRemoveKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup, ReplyKeyboardRemove}

import scala.concurrent.ExecutionContext

class DefaultCreateS10nDialogServiceSpec
  extends AnyFlatSpec with Matchers with DiffMatcher with PartialFunctionValues with MockFactory {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.create[IO].unsafeRunSync

  private val userRepo = new InMemoryUserRepository
  private val mockS10nRepo = mock[SubscriptionRepository[Id]]
  private val stateMessageService = new StateMessageService[IO](new CalendarService)
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepo)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))
  private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, Id](
    mockS10nRepo, userRepo, stateMessageService, dialogEngine, s10nsListMessageService
  )
  private val createS10nDialogService = new DefaultCreateS10nDialogService[IO, Id](
    userRepo, createS10nDialogFsmService, stateMessageService, dialogEngine
  )

  private val user = User(UserId(0L), FirstName("John"), None)
  private val draft = SubscriptionDraft.create(UserId(0L))

  "onCreateCommand" should "ask for a subscription's currency" in {
    createS10nDialogService.onCreateCommand(user).unsafeRunSync should matchTo {
      ReplyMessage(
        "Currency:",
        markup = MarkupReplyKeyboard(ReplyKeyboardMarkup(
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
        )).some
      )
    }
  }

  "onCreateWithDefaultCurrencyCommand" should "ask for a subscription's name" in {
    createS10nDialogService.onCreateWithDefaultCurrencyCommand(user).unsafeRunSync should matchTo {
      ReplyMessage("Name:", MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some)
    }
  }

  behavior of "saveDraft"

  it should "fail if a text is missing" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    createS10nDialogService.saveDraft.valueAt(user, dialog, None).unsafeRunSync shouldBe
      TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a name is too long" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    val name = List.fill(257)("a").mkString
    createS10nDialogService.saveDraft.valueAt(user, dialog, name.some).unsafeRunSync shouldBe
      NameTooLong.invalidNec[String]
  }

  it should "fail if a currency's code is unknown" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Currency, draft)
    val code = "AAA"
    createS10nDialogService.saveDraft.valueAt(user, dialog, code.some).unsafeRunSync shouldBe
      UnknownCurrency.invalidNec[CurrencyUnit]
  }

  it should "fail if an amount is not a number" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    val amountText = "NaN"
    createS10nDialogService.saveDraft.valueAt(user, dialog, amountText.some).unsafeRunSync shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if an amount is not positive" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    val amountText = "-3"
    createS10nDialogService.saveDraft.valueAt(user, dialog, amountText.some).unsafeRunSync shouldBe
      NumberMustBePositive.invalidNec[BigDecimal]
  }

  it should "fail if a duration is not a number" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    val durationText = "NaN"
    createS10nDialogService.saveDraft.valueAt(user, dialog, durationText.some).unsafeRunSync shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if a duration is not positive" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    val durationText = "-1"
    createS10nDialogService.saveDraft.valueAt(user, dialog, durationText.some).unsafeRunSync shouldBe
      NumberMustBePositive.invalidNec[BigDecimal]
  }
}