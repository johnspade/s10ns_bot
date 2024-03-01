package ru.johnspade.s10ns.subscription.service
import telegramium.bots.CallbackQuery
import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.S10nsPeriod
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.user.User

trait SubscriptionListService[F[_]] {
  def onSubscriptionsCb(user: User, cb: CallbackQuery, data: S10ns): F[ReplyMessage]

  def onS10nsPeriodCb(user: User, cb: CallbackQuery, data: S10nsPeriod): F[ReplyMessage]

  def onRemoveSubscriptionCb(user: User, cb: CallbackQuery, data: RemoveS10n): F[ReplyMessage]

  def onSubcriptionCb(user: User, cb: CallbackQuery, data: S10n): F[Either[String, ReplyMessage]]

  def onListCommand(from: User, page: PageNumber): F[ReplyMessage]

  def onEditS10nCb(cb: CallbackQuery, data: EditS10n): F[Either[String, InlineKeyboardMarkup]]

  def onNotifyCb(user: User, cb: CallbackQuery, data: Notify): F[Either[String, InlineKeyboardMarkup]]
}
