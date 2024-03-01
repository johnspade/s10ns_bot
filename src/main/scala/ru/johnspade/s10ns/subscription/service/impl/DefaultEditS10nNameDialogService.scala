package ru.johnspade.s10ns.subscription.service.impl

import cats.Monad
import cats.~>

import com.softwaremill.quicklens._

import ru.johnspade.s10ns.bot.EditS10nName
import ru.johnspade.s10ns.bot.EditS10nNameDialog
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.StateMessageService
import ru.johnspade.s10ns.bot.engine.TransactionalDialogEngine
import ru.johnspade.s10ns.subscription.dialog.EditS10nNameDialogEvent
import ru.johnspade.s10ns.subscription.dialog.EditS10nNameDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.EditS10nNameDialogService
import ru.johnspade.s10ns.subscription.service.RepliesValidated
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.subscription.tags.SubscriptionName
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

class DefaultEditS10nNameDialogService[F[_]: Monad, D[_]: Monad](
  s10nsListMessageService: S10nsListMessageService[F],
  stateMessageService: StateMessageService[F, EditS10nNameDialogState],
  userRepo: UserRepository[D],
  s10nRepo: SubscriptionRepository[D],
  dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
  extends EditS10nDialogService[F, D, EditS10nNameDialogState](
    s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
  ) with EditS10nNameDialogService[F] {
  def onEditS10nNameCb(user: User, data: EditS10nName): F[List[ReplyMessage]] =
    onEditS10nDialogCb(
      user = user,
      s10nId = data.subscriptionId,
      state = EditS10nNameDialogState.Name,
      createDialog =
        s10n => EditS10nNameDialog(
          state = EditS10nNameDialogState.Name,
          draft = s10n
        )
    )

  def saveName(user: User, dialog: EditS10nNameDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(name => validateNameLength(SubscriptionName(name)))
      .traverse(saveName(user, dialog, _))

  private def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.name).setTo(name)
    transition(user, updatedDialog)(EditS10nNameDialogEvent.EnteredName)
  }
}
