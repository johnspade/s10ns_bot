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
import ru.johnspade.s10ns.telegram.{OneTime, PeriodUnit, ReplyMessage}
import ru.johnspade.s10ns.user.{CreateS10nDialogState, EditS10nOneTimeDialogState, StateWithMessage}
import telegramium.bots.{InlineKeyboardMarkup, KeyboardButton, KeyboardMarkup, MarkupInlineKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService[F]) {
  def getMessage(state: CreateS10nDialogState): F[ReplyMessage] =
    state match {
      case CreateS10nDialogState.Currency => getMessagePure(state.message, MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case CreateS10nDialogState.BillingPeriodUnit => getMessagePure(state.message, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case CreateS10nDialogState.IsOneTime => getMessagePure(state.message, MarkupInlineKeyboard(IsOneTimeReplyMarkup).some)
      case CreateS10nDialogState.FirstPaymentDate =>
        for {
          date <- Sync[F].delay(LocalDate.now)
          kb <- calendarService.generateKeyboard(date)
        } yield ReplyMessage(state.message, MarkupInlineKeyboard(kb).some)
      case CreateS10nDialogState.Finished => getMessagePure(state.message, BotStart.markup.some)
      case _ => getMessagePure(state.message)
    }

  def getMessage(state: StateWithMessage): F[ReplyMessage] = // todo
    state match {
      case EditS10nOneTimeDialogState.IsOneTime =>
        getMessagePure(state.message, MarkupInlineKeyboard(IsOneTimeReplyMarkup).some)
      case EditS10nOneTimeDialogState.BillingPeriodUnit =>
        getMessagePure(state.message, MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case _ => getMessagePure(state.message)
    }

  private def getMessagePure(message: String, markup: Option[KeyboardMarkup] = None): F[ReplyMessage] =
    Sync[F].pure(ReplyMessage(message, markup))

  private val BillingPeriodUnitReplyMarkup = InlineKeyboardMarkup(
    List(List(ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS).map { unit =>
      inlineKeyboardButton(unit.toString, PeriodUnit(BillingPeriodUnit(unit)))
    })
  )

  private val IsOneTimeReplyMarkup = InlineKeyboardMarkup(List(List(
    inlineKeyboardButton("Recurring", OneTime(OneTimeSubscription(false))),
    inlineKeyboardButton("One time", OneTime(OneTimeSubscription(true)))
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
