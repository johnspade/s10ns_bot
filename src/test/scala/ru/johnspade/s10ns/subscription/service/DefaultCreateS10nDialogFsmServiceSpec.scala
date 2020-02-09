package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate

import cats.Id
import cats.effect.{Clock, IO}
import cats.syntax.option._
import com.softwaremill.diffx.scalatest.DiffMatcher
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, MessageParseMode, ReplyMessage}
import ru.johnspade.s10ns.bot.{BotStart, CreateS10nDialog, EditS10n, MoneyService, RemoveS10n, S10ns, StateMessageService}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.dialog.CreateS10nDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup, MarkupInlineKeyboard}

import scala.concurrent.ExecutionContext

class DefaultCreateS10nDialogFsmServiceSpec extends AnyFlatSpec with Matchers with DiffMatcher with MockFactory {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock
  private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.create[IO].unsafeRunSync

  private val mockS10nRepo = mock[SubscriptionRepository[Id]]
  private val userRepository = new InMemoryUserRepository
  private val stateMessageService = new StateMessageService[IO](new CalendarService)
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepository)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))
  private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, Id](
    mockS10nRepo, userRepository, stateMessageService, dialogEngine, s10nsListMessageService
  )

  private val user = User(UserId(0L), FirstName("John"), None)
  private val draft = SubscriptionDraft.create(UserId(0L))
  private val s10n = Subscription.fromDraft(draft, SubscriptionId(0L))
  private val finish = List(
    ReplyMessage("Saved.", BotStart.markup.some),
    ReplyMessage(
      "**\n\n0.00 EUR\n",
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("Edit", EditS10n(SubscriptionId(0L), PageNumber(0)))),
        List(inlineKeyboardButton("Remove", RemoveS10n(SubscriptionId(0L), PageNumber(0)))),
        List(inlineKeyboardButton("List", S10ns(PageNumber(0))))
      ))).some,
      MessageParseMode.Markdown.some
    )
  )


  "saveName" should "ask for amount" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    createS10nDialogFsmService.saveName(user, dialog, SubscriptionName("Netflix")).unsafeRunSync should matchTo {
      List(ReplyMessage("Amount:"))
    }
  }

  "saveCurrency" should "ask for a name" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Currency, draft)
    createS10nDialogFsmService.saveCurrency(user, dialog, CurrencyUnit.EUR).unsafeRunSync should matchTo {
      List(ReplyMessage("Name:"))
    }
  }

  "saveAmount" should "ask is it an one time subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    createS10nDialogFsmService.saveAmount(user, dialog, BigDecimal(1)).unsafeRunSync should matchTo {
      List(ReplyMessage(
        "Recurring/one time:",
        markup = MarkupInlineKeyboard(InlineKeyboardMarkup(
          List(List(
            InlineKeyboardButton("Recurring", callbackData = "OneTime\u001Dfalse".some),
            InlineKeyboardButton("One time", callbackData = "OneTime\u001Dtrue".some),
            InlineKeyboardButton("Skip", callbackData = "SkipIsOneTime".some)
          ))
        )).some
      ))
    }
  }

  "saveBillingPeriodDuration" should "ask for a first payment date" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    createS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, BillingPeriodDuration(1)).unsafeRunSync should
      matchTo {
        List(ReplyMessage(
          "First payment date:",
          markup = MarkupInlineKeyboard((new CalendarService).generateKeyboard(LocalDate.now)).some
        ))
      }
  }

  "saveBillingPeriodUnit" should "ask for a billing period duration" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodUnit, draft)
    createS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, BillingPeriodUnit.Month).unsafeRunSync should
      matchTo {
        List(ReplyMessage("Billing period duration (1, 2...):"))
      }
  }

  "skipIsOneTime" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.skipIsOneTime(user, dialog).unsafeRunSync should matchTo(finish)
  }

  "saveIsOneTime" should "ask for a billing period unit of a recurring subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.saveIsOneTime(user, dialog, OneTimeSubscription(false)).unsafeRunSync should matchTo {
      List(ReplyMessage(
        "Billing period unit:",
        MarkupInlineKeyboard(InlineKeyboardMarkup(
          List(List(
            InlineKeyboardButton("Days", callbackData = "PeriodUnit\u001DDay".some),
            InlineKeyboardButton("Weeks", callbackData = "PeriodUnit\u001DWeek".some),
            InlineKeyboardButton("Months", callbackData = "PeriodUnit\u001DMonth".some),
            InlineKeyboardButton("Years", callbackData = "PeriodUnit\u001DYear".some)
          ))
        )).some
      ))
    }
  }

  it should "ask for a first payment date of an one time subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.saveIsOneTime(user, dialog, OneTimeSubscription(true)).unsafeRunSync should matchTo {
      List(ReplyMessage(
        "First payment date:",
        markup = MarkupInlineKeyboard((new CalendarService).generateKeyboard(LocalDate.now)).some
      ))
    }
  }

  "skipFirstPaymentDate" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.FirstPaymentDate, draft)
    createS10nDialogFsmService.skipFirstPaymentDate(user, dialog).unsafeRunSync should matchTo(finish)
  }

  "saveFirstPaymentDate" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.FirstPaymentDate, draft)
    createS10nDialogFsmService.saveFirstPaymentDate(user, dialog, FirstPaymentDate(LocalDate.now)).unsafeRunSync should
      matchTo(finish)
  }
}
