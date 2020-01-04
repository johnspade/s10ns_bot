package ru.johnspade.s10ns.bot.engine

import cats.effect.Sync
import cats.implicits._
import doobie.free.connection
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.bot.{BotStart, Dialog}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.{MarkupRemoveKeyboard, ReplyKeyboardRemove}

class DialogEngine[F[_] : Sync](
  private val userRepo: UserRepository
)(private implicit val xa: Transactor[F]) {
  def startDialog(user: User, dialog: Dialog, message: ReplyMessage): F[ReplyMessage] = {
    val userWithDialog = user.copy(dialog = dialog.some)
    val reply = if (message.markup.isDefined) message
    else message.copy(
      markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some
    )
    userRepo.createOrUpdate(userWithDialog)
      .transact(xa) *>
      Sync[F].pure(reply)
  }

  def reset(user: User, message: String): ConnectionIO[ReplyMessage] =
    reset(user)
      .map(_ => ReplyMessage(text = message, markup = BotStart.markup.some))

  def resetAndCommit(user: User, message: String): F[ReplyMessage] =
    reset(user, message).transact(xa)

  def sayHi(user: User): F[ReplyMessage] =
    reset(user)
      .map(_ => BotStart.message)
      .transact(xa)

  private def reset(user: User): ConnectionIO[Unit] =
    user.dialog
      .map { _ =>
        val resetUser = user.copy(dialog = None)
        userRepo.update(resetUser).void
      }
      .getOrElse(connection.unit)
}
