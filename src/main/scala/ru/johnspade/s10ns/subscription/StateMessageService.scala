package ru.johnspade.s10ns.subscription

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.effect.Sync
import cats.implicits._
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.help.BotStart
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.telegram.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.telegram.{OneTime, PeriodUnit, ReplyMessage, SkipIsOneTime}
import ru.johnspade.s10ns.user.{CreateS10nDialogState, DialogState, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nFirstPaymentDateDialogState, EditS10nOneTimeDialogState}
import telegramium.bots.{InlineKeyboardMarkup, KeyboardButton, KeyboardMarkup, MarkupInlineKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService[F]) {
  def getMessage(state: CreateS10nDialogState): F[ReplyMessage] =
    state match {
      case CreateS10nDialogState.Currency => getMessagePure(state, MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case CreateS10nDialogState.BillingPeriodUnit => getMessagePure(state, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case CreateS10nDialogState.IsOneTime => getMessagePure(state, MarkupInlineKeyboard(isOneTimeReplyMarkup("Skip")).some)
      case CreateS10nDialogState.FirstPaymentDate =>
        for {
          date <- Sync[F].delay(LocalDate.now)
          kb <- calendarService.generateKeyboard(date)
        } yield ReplyMessage(state.message, MarkupInlineKeyboard(kb).some)
      case CreateS10nDialogState.Finished => getMessagePure(state, BotStart.markup.some)
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

  def getMessage(state: EditS10nBillingPeriodDialogState): F[ReplyMessage] =
    state match {
      case EditS10nBillingPeriodDialogState.BillingPeriodUnit =>
        getMessagePure(state, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case _ => getTextMessage(state)
    }

  def getMessage(state: EditS10nFirstPaymentDateDialogState): F[ReplyMessage] =
    state match {
      case EditS10nFirstPaymentDateDialogState.FirstPaymentDate =>
        for {
          date <- Sync[F].delay(LocalDate.now)
          kb <- calendarService.generateKeyboard(date)
        } yield ReplyMessage(state.message, MarkupInlineKeyboard(kb).some)
      case EditS10nFirstPaymentDateDialogState.Finished => getMessagePure(state)
    }

  def getTextMessage(state: DialogState): F[ReplyMessage] =
    getMessagePure(state)

  private def getMessagePure(state: DialogState, markup: Option[KeyboardMarkup] = None): F[ReplyMessage] =
    Sync[F].pure(ReplyMessage(state.message, markup))

  private val BillingPeriodUnitReplyMarkup = InlineKeyboardMarkup(
    List(List(ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS).map { unit =>
      inlineKeyboardButton(unit.toString, PeriodUnit(BillingPeriodUnit(unit)))
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
}
