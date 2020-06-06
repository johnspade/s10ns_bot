package ru.johnspade.s10ns.bot

import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.tags.OneTimeSubscription
import telegramium.bots.high._

object Markup {
  val CurrencyReplyMarkup: ReplyKeyboardMarkup = ReplyKeyboardMarkup(
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

  def isOneTimeReplyMarkup(skipIsOneTimeMessage: String): InlineKeyboardMarkup =
    InlineKeyboardMarkup(
      List(
        List(
          inlineKeyboardButton("Recurring", OneTime(OneTimeSubscription(false))),
          inlineKeyboardButton("One time", OneTime(OneTimeSubscription(true))),
          inlineKeyboardButton("Every month", EveryMonth)
        ),
        List(inlineKeyboardButton(skipIsOneTimeMessage, SkipIsOneTime))
      )
    )

  val BillingPeriodUnitReplyMarkup: InlineKeyboardMarkup = InlineKeyboardMarkup.singleRow(
    List(BillingPeriodUnit.Day, BillingPeriodUnit.Week, BillingPeriodUnit.Month, BillingPeriodUnit.Year).map { unit =>
      inlineKeyboardButton(unit.chronoUnit.toString, PeriodUnit(unit))
    }
  )
}
