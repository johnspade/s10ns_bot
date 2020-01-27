package ru.johnspade.s10ns.subscription.service
import ru.johnspade.s10ns.bot.{EditS10n, RemoveS10n, S10n, S10ns}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.user.User
import telegramium.bots.{CallbackQuery, InlineKeyboardMarkup}

trait SubscriptionListService[F[_]] {
  def onSubscriptionsCb(user: User, cb: CallbackQuery, data: S10ns): F[ReplyMessage]

  def onRemoveSubscriptionCb(user: User, cb: CallbackQuery, data: RemoveS10n): F[ReplyMessage]

  def onSubcriptionCb(user: User, cb: CallbackQuery, data: S10n): F[Either[String, ReplyMessage]]

  def onListCommand(from: User, page: PageNumber): F[ReplyMessage]

  def onEditS10nCb(cb: CallbackQuery, data: EditS10n): F[Either[String, InlineKeyboardMarkup]]
}
