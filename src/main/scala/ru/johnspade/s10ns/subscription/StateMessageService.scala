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
import ru.johnspade.s10ns.user.{CreateS10nDialogState, EditS10nDialogState}
import telegramium.bots.{InlineKeyboardMarkup, KeyboardButton, KeyboardMarkup, MarkupInlineKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService[F]) {
  def getMessage(state: CreateS10nDialogState): F[ReplyMessage] = {
    def getMessagePure(markup: Option[KeyboardMarkup] = None): F[ReplyMessage] =
      Sync[F].pure(ReplyMessage(state.message, markup))

    state match {
      case CreateS10nDialogState.Currency => getMessagePure(MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case CreateS10nDialogState.BillingPeriodUnit => getMessagePure(MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case CreateS10nDialogState.IsOneTime => getMessagePure(MarkupInlineKeyboard(IsOneTimeReplyMarkup).some)
      case CreateS10nDialogState.FirstPaymentDate =>
        for {
          date <- Sync[F].delay(LocalDate.now)
          kb <- calendarService.generateKeyboard(date)
        } yield ReplyMessage(state.message, MarkupInlineKeyboard(kb).some)
      case CreateS10nDialogState.Finished => getMessagePure(BotStart.markup.some)
      case _ => getMessagePure()
    }
  }

  def getMessage(state: EditS10nDialogState): F[ReplyMessage] = Sync[F].pure(ReplyMessage(state.message))

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
