package ru.johnspade.s10ns.bot.engine

import cats.Applicative
import cats.Monad
import cats.effect.Sync
import cats.implicits._
import cats.~>

import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.ReplyKeyboardMarkup
import telegramium.bots.ReplyKeyboardRemove

import ru.johnspade.s10ns.bot.BotStart
import ru.johnspade.s10ns.bot.Dialog
import ru.johnspade.s10ns.bot.Errors
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

class DefaultDialogEngine[F[_] : Sync, D[_] : Applicative](
  private val userRepo: UserRepository[D]
)(private implicit val transact: D ~> F) extends TransactionalDialogEngine[F, D] {
  override def startDialog(user: User, dialog: Dialog, message: ReplyMessage): F[List[ReplyMessage]] = {
    if (user.dialog.isEmpty) {
      val userWithDialog = user.copy(dialog = dialog.some)
      val replyKeyboardRemove = ReplyKeyboardRemove(removeKeyboard = true).some
      val reply = message.markup match {
        case Some(replyKeyboard: ReplyKeyboardMarkup) => List(
          message.copy(markup = replyKeyboard.some)
        )
        case Some(inlineKeyboard: InlineKeyboardMarkup) => List(
          message.copy(markup = replyKeyboardRemove),
          ReplyMessage(
            text = "\uD83D\uDD18/☑️",
            markup = inlineKeyboard.some
          )
        )
        case _ => List(
          message.copy(markup = replyKeyboardRemove)
        )
      }
      transact(userRepo.createOrUpdate(userWithDialog)).map(_ => reply)
    }
    else
      Monad[F].pure(List(ReplyMessage(Errors.ActiveDialogNotFinished)))
  }

  override def reset(user: User, message: String): D[ReplyMessage] =
    reset(user)
      .map(_ => ReplyMessage(text = message, markup = BotStart.markup.some))

  override def resetAndCommit(user: User, message: String): F[ReplyMessage] =
    transact(reset(user, message))

  override def sayHi(user: User): F[ReplyMessage] =
    transact {
      reset(user)
        .map(_ => BotStart.message)
    }

  private def reset(user: User): D[Unit] =
    user.dialog
      .map { _ =>
        val resetUser = user.copy(dialog = None)
        userRepo.update(resetUser).void
      }
      .getOrElse(Applicative[D].unit)
}
