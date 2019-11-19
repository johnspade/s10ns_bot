package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import cats.implicits._
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.telegram.TelegramOps.toReplyMessage
import ru.johnspade.s10ns.user.{EditS10nNameDialogState, User}
import telegramium.bots.Message

class EditS10nDialogController[F[_] : Sync](
  private val editS10nDialogService: EditS10nDialogService[F]
) {
  def s10nNameMessage(user: User, message: Message): F[ReplyMessage] =
    user.editS10nNameDialogState.flatMap {
      case EditS10nNameDialogState.Name => editS10nDialogService.saveName(user, message.text).map(toReplyMessage).some
      case _ => Option.empty[F[ReplyMessage]]
    } getOrElse Sync[F].pure(ReplyMessage(Errors.default))
}
