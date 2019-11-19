package ru.johnspade.s10ns.subscription

import cats.effect.Sync
import ru.johnspade.s10ns.common.ValidatorNec.{ValidationResult, validateNameLength, validateText}
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.user.{EditS10nDialogFsmService, User}

class EditS10nDialogService[F[_] : Sync](
  private val editS10nDialogFsmService: EditS10nDialogFsmService[F]
) {
  def saveName(user: User, text: Option[String]): F[ValidationResult[ReplyMessage]] =
    validateText(text)
      .andThen(name => validateNameLength(SubscriptionName(name)))
      .traverse(editS10nDialogFsmService.saveName(user, _))
}
