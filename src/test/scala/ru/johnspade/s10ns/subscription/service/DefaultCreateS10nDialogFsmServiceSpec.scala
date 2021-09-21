package ru.johnspade.s10ns.subscription.service

import java.time.{LocalDate, ZoneOffset}
import cats.Id
import cats.effect.{Clock, IO}
import cats.syntax.option._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, ReplyMessage}
import ru.johnspade.s10ns.bot.{BotStart, CreateS10nDialog, EditS10n, MoneyService, Notify, RemoveS10n, S10ns}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nDialogState, CreateS10nMsgService}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.impl.DefaultCreateS10nDialogFsmService
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import telegramium.bots.{Markdown, ReplyKeyboardRemove, InlineKeyboardMarkup}
import telegramium.bots.high.keyboards.{InlineKeyboardButtons, InlineKeyboardMarkups}
import tofu.logging.Logs
import com.softwaremill.diffx.generic.auto._

import cats.effect.unsafe.implicits.global

class DefaultCreateS10nDialogFsmServiceSpec extends AnyFlatSpec with Matchers with DiffMatcher with MockFactory {
  private implicit val logs: Logs[IO, IO] = Logs.sync[IO, IO]

  private val mockS10nRepo = mock[SubscriptionRepository[Id]]
  private val userRepository = new InMemoryUserRepository
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepository)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](
    moneyService,
    new S10nInfoService[IO],
    new S10nsListReplyMessageService
  )
  private val calendarService = new CalendarService
  private val createS10nMsgService = new CreateS10nMsgService[IO](calendarService)
  private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, Id](
    mockS10nRepo, userRepository, dialogEngine, s10nsListMessageService, createS10nMsgService
  )

  private val s10nId = SubscriptionId(0L)
  private val page = PageNumber(0)

  private val user = User(UserId(0L), FirstName("John"), None)
  private val draft = SubscriptionDraft.create(UserId(0L))
  private val s10n = Subscription.fromDraft(draft, s10nId)
  private val finish = List(
    ReplyMessage("Saved.", BotStart.markup.some),
    ReplyMessage(
      "**\n\n0.00 €\n",
      InlineKeyboardMarkups.singleColumn(List(
        inlineKeyboardButton("Edit", EditS10n(s10nId, page)),
        inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, page)),
        inlineKeyboardButton("Remove", RemoveS10n(s10nId, page)),
        inlineKeyboardButton("List", S10ns(page))
      )).some,
      Markdown.some
    )
  )


  "saveName" should "ask for amount" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Name, draft)
    createS10nDialogFsmService.saveName(user, dialog, SubscriptionName("Netflix")).unsafeRunSync() should matchTo {
      List(ReplyMessage("Amount:"))
    }
  }

  "saveCurrency" should "ask for a name" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Currency, draft)
    createS10nDialogFsmService.saveCurrency(user, dialog, CurrencyUnit.EUR).unsafeRunSync() should matchTo {
      List(ReplyMessage("Name:", ReplyKeyboardRemove(removeKeyboard = true).some))
    }
  }

  "saveAmount" should "ask is it an one time subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.Amount, draft)
    createS10nDialogFsmService.saveAmount(user, dialog, BigDecimal(1)).unsafeRunSync() should matchTo {
      List(ReplyMessage(
        "Recurring/one time:",
        markup = InlineKeyboardMarkup(
          List(
            List(
              InlineKeyboardButtons.callbackData("Recurring", "OneTime\u001Dfalse"),
              InlineKeyboardButtons.callbackData("One time", "OneTime\u001Dtrue"),
              InlineKeyboardButtons.callbackData("Every month", "EveryMonth")
            ),
            List(InlineKeyboardButtons.callbackData("Skip", "SkipIsOneTime"))
          )
        ).some
      ))
    }
  }

  "saveBillingPeriodDuration" should "ask for a first payment date" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodDuration, draft)
    createS10nDialogFsmService.saveBillingPeriodDuration(user, dialog, BillingPeriodDuration(1)).unsafeRunSync() should
      matchTo {
        List(ReplyMessage(
          "First payment date:",
          calendarService.generateDaysKeyboard(LocalDate.now(ZoneOffset.UTC)).some
        ))
      }
  }

  "saveBillingPeriodUnit" should "ask for a billing period duration" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.BillingPeriodUnit, draft)
    createS10nDialogFsmService.saveBillingPeriodUnit(user, dialog, BillingPeriodUnit.Month).unsafeRunSync() should
      matchTo {
        List(ReplyMessage("Billing period duration (1, 2...):"))
      }
  }

  "skipIsOneTime" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.skipIsOneTime(user, dialog).unsafeRunSync() should matchTo(finish)
  }

  "saveIsOneTime" should "ask for a billing period unit of a recurring subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.saveIsOneTime(user, dialog, OneTimeSubscription(false)).unsafeRunSync() should matchTo {
      List(ReplyMessage(
        "Billing period unit:",
        InlineKeyboardMarkups.singleRow(
          List(
            InlineKeyboardButtons.callbackData("Days", "PeriodUnit\u001DDay"),
            InlineKeyboardButtons.callbackData("Weeks", "PeriodUnit\u001DWeek"),
            InlineKeyboardButtons.callbackData("Months", "PeriodUnit\u001DMonth"),
            InlineKeyboardButtons.callbackData("Years", "PeriodUnit\u001DYear")
          )
        ).some
      ))
    }
  }

  it should "ask for a first payment date of an one time subscription" in {
    val dialog = CreateS10nDialog(CreateS10nDialogState.IsOneTime, draft)
    createS10nDialogFsmService.saveIsOneTime(user, dialog, OneTimeSubscription(true)).unsafeRunSync() should matchTo {
      List(ReplyMessage(
        "First payment date:",
        markup = calendarService.generateDaysKeyboard(LocalDate.now(ZoneOffset.UTC)).some
      ))
    }
  }

  "skipFirstPaymentDate" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.FirstPaymentDate, draft)
    createS10nDialogFsmService.skipFirstPaymentDate(user, dialog).unsafeRunSync() should matchTo(finish)
  }

  "saveFirstPaymentDate" should "finish a dialog" in {
    (mockS10nRepo.create _).expects(*).returns(s10n)

    val dialog = CreateS10nDialog(CreateS10nDialogState.FirstPaymentDate, draft)
    createS10nDialogFsmService.saveFirstPaymentDate(user, dialog, FirstPaymentDate(LocalDate.now(ZoneOffset.UTC))).unsafeRunSync() should
      matchTo(finish)
  }
}
