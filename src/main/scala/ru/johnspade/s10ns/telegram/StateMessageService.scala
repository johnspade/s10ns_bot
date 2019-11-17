package ru.johnspade.s10ns.telegram

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.effect.Sync
import cats.implicits._
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.help.StartMarkup
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, OneTimeSubscription}
import ru.johnspade.s10ns.user.SubscriptionDialogState
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup, KeyboardButton, KeyboardMarkup, MarkupInlineKeyboard, MarkupReplyKeyboard, ReplyKeyboardMarkup}

class StateMessageService[F[_] : Sync](private val calendarService: CalendarService[F]) {
  def getMessage(state: SubscriptionDialogState): F[ReplyMessage] = {
    def getMessagePure(markup: Option[KeyboardMarkup] = None): F[ReplyMessage] =
      Sync[F].pure(ReplyMessage(state.message, markup))

    state match {
      case SubscriptionDialogState.Currency => getMessagePure(MarkupReplyKeyboard(CurrencyReplyMarkup).some)
      case SubscriptionDialogState.BillingPeriodUnit => getMessagePure(MarkupInlineKeyboard(BillingPeriodUnitReplyMarkup).some)
      case SubscriptionDialogState.IsOneTime => getMessagePure(MarkupInlineKeyboard(IsOneTimeReplyMarkup).some)
      case SubscriptionDialogState.FirstPaymentDate =>
        for {
          date <- Sync[F].delay(LocalDate.now)
          kb <- calendarService.generateKeyboard(date)
        } yield ReplyMessage(state.message, MarkupInlineKeyboard(kb).some)
      case SubscriptionDialogState.Finished => getMessagePure(StartMarkup.markup.some)
      case _ => getMessagePure()
    }
  }

  private val BillingPeriodUnitReplyMarkup = InlineKeyboardMarkup(
    List(List(ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS).map { unit =>
      InlineKeyboardButton(unit.toString, callbackData = CallbackData.billingPeriodUnit(BillingPeriodUnit(unit)).some)
    })
  )

  private val IsOneTimeReplyMarkup = InlineKeyboardMarkup(List(List(
    InlineKeyboardButton("Recurring", callbackData = CallbackData.oneTime(OneTimeSubscription(false)).some),
    InlineKeyboardButton("One time", callbackData = CallbackData.oneTime(OneTimeSubscription(true)).some)
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
