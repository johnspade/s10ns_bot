package ru.johnspade.s10ns.bot.engine

import cats.effect.Sync
import cats.implicits._
import cats.{Applicative, ~>}
import ru.johnspade.s10ns.bot.{BotStart, Dialog}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.{MarkupRemoveKeyboard, ReplyKeyboardRemove}

class DialogEngine[F[_] : Sync, D[_] : Applicative](
  private val userRepo: UserRepository[D]
)(private implicit val transact: D ~> F) {
  def startDialog(user: User, dialog: Dialog, message: ReplyMessage): F[ReplyMessage] = {
    val userWithDialog = user.copy(dialog = dialog.some)
    val reply = if (message.markup.isDefined) message // todo remove default keyboard
    else message.copy(
      markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some
    )
    transact(userRepo.createOrUpdate(userWithDialog)) *>
      Sync[F].pure(reply)
  }

  def reset(user: User, message: String): D[ReplyMessage] =
    reset(user)
      .map(_ => ReplyMessage(text = message, markup = BotStart.markup.some))

  def resetAndCommit(user: User, message: String): F[ReplyMessage] =
    transact(reset(user, message))

  def sayHi(user: User): F[ReplyMessage] =
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
