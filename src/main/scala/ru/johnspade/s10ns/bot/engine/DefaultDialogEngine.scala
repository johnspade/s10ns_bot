package ru.johnspade.s10ns.bot.engine

import cats.effect.Sync
import cats.implicits._
import cats.{Applicative, ~>}
import ru.johnspade.s10ns.bot.{BotStart, Dialog}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.{InlineKeyboardMarkup, ReplyKeyboardMarkup, ReplyKeyboardRemove}

class DefaultDialogEngine[F[_] : Sync, D[_] : Applicative](
  private val userRepo: UserRepository[D]
)(private implicit val transact: D ~> F) extends TransactionalDialogEngine[F, D] {
  override def startDialog(user: User, dialog: Dialog, message: ReplyMessage): F[List[ReplyMessage]] = {
    val userWithDialog = user.copy(dialog = dialog.some)
    val replyKeyboardRemove = ReplyKeyboardRemove(removeKeyboard = true).some
    val reply = message.markup match {
      case Some(replyKeyboard @ ReplyKeyboardMarkup(_, _, _, _)) => List(
        message.copy(
          markup = replyKeyboard.some
        )
      )
      case Some(inlineKeyboard @ InlineKeyboardMarkup(_)) => List(
        message.copy(
          markup = replyKeyboardRemove,
        ),
        ReplyMessage(
          text = "⌨️",
          markup = inlineKeyboard.some
        )
      )
      case _ => List(
        message.copy(
          markup = replyKeyboardRemove
        )
      )
    }
    transact(userRepo.createOrUpdate(userWithDialog)).map(_ => reply)
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
