package ru.johnspade.s10ns.bot

import java.time.LocalDate

import cats.effect.Sync
import cats.implicits._
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DialogState, ReplyMessage}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.settings.SettingsDialogState
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nDialogState, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nFirstPaymentDateDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.tags._
import telegramium.bots.{InlineKeyboardMarkup, KeyboardButton, KeyboardMarkup, MarkupInlineKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService) {
  def getMessage(state: CreateS10nDialogState): F[ReplyMessage] =
    state match {
      case CreateS10nDialogState.Currency => getMessagePure(state, MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case CreateS10nDialogState.BillingPeriodUnit => getMessagePure(state, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case CreateS10nDialogState.IsOneTime => getMessagePure(state, MarkupInlineKeyboard(isOneTimeReplyMarkup("Skip")).some)
      case CreateS10nDialogState.FirstPaymentDate => createMessageWithCalendar(state.message)
      case CreateS10nDialogState.Finished => getMessagePure(state, BotStart.markup.some)
      case _ => getMessagePure(state)
    }

  def getMessage(state: SettingsDialogState): F[ReplyMessage] =
    state match {
      case SettingsDialogState.DefaultCurrency => getMessagePure(state, MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case _ => getMessagePure(state)
    }

  def getMessage(state: EditS10nOneTimeDialogState): F[ReplyMessage] =
    state match {
      case EditS10nOneTimeDialogState.IsOneTime =>
        getMessagePure(state, MarkupInlineKeyboard(isOneTimeReplyMarkup("Do not fill (remove)")).some)
      case EditS10nOneTimeDialogState.BillingPeriodUnit =>
        getMessagePure(state, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nAmountDialogState): F[ReplyMessage] =
    getTextMessage(state)

  def getMessage(state: EditS10nCurrencyDialogState): F[ReplyMessage] =
    state match {
      case EditS10nCurrencyDialogState.Currency => getMessagePure(state, MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nBillingPeriodDialogState): F[ReplyMessage] =
    state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodUnit =>
        getMessagePure(state, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nFirstPaymentDateDialogState): F[ReplyMessage] =
    state match {
      case EditS10nFirstPaymentDateDialogState.FirstPaymentDate => createMessageWithCalendar(state.message)
      case EditS10nFirstPaymentDateDialogState.Finished => getMessagePure(state)
    }

  def getTextMessage(state: DialogState): F[ReplyMessage] =
    getMessagePure(state)

  private def getMessagePure(state: DialogState, markup: Option[KeyboardMarkup] = None): F[ReplyMessage] =
    Sync[F].pure(ReplyMessage(state.message, markup))

  private val BillingPeriodUnitReplyMarkup = InlineKeyboardMarkup(
    List(List(BillingPeriodUnit.Day, BillingPeriodUnit.Week, BillingPeriodUnit.Month, BillingPeriodUnit.Year).map { unit =>
      inlineKeyboardButton(unit.chronoUnit.toString, PeriodUnit(unit))
    })
  )

  private def isOneTimeReplyMarkup(skipIsOneTimeMessage: String) =
    InlineKeyboardMarkup(List(List(
      inlineKeyboardButton("Recurring", OneTime(OneTimeSubscription(false))),
      inlineKeyboardButton("One time", OneTime(OneTimeSubscription(true))),
      inlineKeyboardButton(skipIsOneTimeMessage, SkipIsOneTime)
    )))

  private val CurrencyReplyMarkup = ReplyKeyboardMarkup(
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
  )

  private def createMessageWithCalendar(text: String) =
    Sync[F].delay(LocalDate.now)
      .map { date =>
        val kb = calendarService.generateKeyboard(date)
        ReplyMessage(text, MarkupInlineKeyboard(kb).some)
      }
}
