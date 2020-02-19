package ru.johnspade.s10ns.bot

import java.time.{LocalDate, ZoneOffset}

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.bot.engine.{DialogState, ReplyMessage}
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.settings.SettingsDialogState
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nDialogState, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nFirstPaymentDateDialogState, EditS10nOneTimeDialogState}
import telegramium.bots.{KeyboardMarkup, MarkupInlineKeyboard}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService) {
  def getMessage(state: CreateS10nDialogState): F[ReplyMessage] =
    state match {
      case CreateS10nDialogState.Currency => getMessagePure(state, Markup.CurrencyReplyMarkup.some)
      case CreateS10nDialogState.BillingPeriodUnit => getMessagePure(state, Markup.BillingPeriodUnitReplyMarkup.some)
      case CreateS10nDialogState.IsOneTime => getMessagePure(state, Markup.isOneTimeReplyMarkup("Skip").some)
      case CreateS10nDialogState.FirstPaymentDate => createMessageWithCalendar(state.message)
      case CreateS10nDialogState.Finished => getMessagePure(state, BotStart.markup.some)
      case _ => getMessagePure(state)
    }

  def getMessage(state: SettingsDialogState): F[ReplyMessage] =
    state match {
      case SettingsDialogState.DefaultCurrency => getMessagePure(state, Markup.CurrencyReplyMarkup.some)
      case _ => getMessagePure(state)
    }

  def getMessage(state: EditS10nOneTimeDialogState): F[ReplyMessage] =
    state match {
      case EditS10nOneTimeDialogState.IsOneTime =>
        getMessagePure(state, Markup.isOneTimeReplyMarkup("Do not fill (remove)").some)
      case EditS10nOneTimeDialogState.BillingPeriodUnit =>
        getMessagePure(state, Markup.BillingPeriodUnitReplyMarkup.some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nAmountDialogState): F[ReplyMessage] =
    getTextMessage(state)

  def getMessage(state: EditS10nCurrencyDialogState): F[ReplyMessage] =
    state match {
      case EditS10nCurrencyDialogState.Currency =>
        getMessagePure(state, Markup.CurrencyReplyMarkup.some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nBillingPeriodDialogState): F[ReplyMessage] =
    state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodUnit =>
        getMessagePure(state, Markup.BillingPeriodUnitReplyMarkup.some)
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

  private def createMessageWithCalendar(text: String) =
    Sync[F].delay(LocalDate.now(ZoneOffset.UTC))
      .map { date =>
        val kb = calendarService.generateKeyboard(date)
        ReplyMessage(text, MarkupInlineKeyboard(kb).some)
      }
}
