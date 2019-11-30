package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{S10nsListMessageService, StateMessageService, Subscription, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.ReplyMessage
import ru.johnspade.s10ns.user.EditS10nNameDialogEvent.EnteredName

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository
)(private implicit val xa: Transactor[F]) {
  def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[ReplyMessage] = {
    def onFinish(draft: Subscription) = {
      val userWithNewState = user.copy(dialog = None)
      for {
        s10nOpt <- userRepo.update(userWithNewState)
          .productR(s10nRepo.update(draft))
          .transact(xa)
        replyOpt <- s10nOpt.traverse { s10n =>
          s10nsListMessageService.createSubscriptionMessage(user, s10n, PageNumber(0))
        }
      } yield replyOpt getOrElse ReplyMessage(Errors.notFound)
    }

    val draft = dialog.draft.copy(name = name)
    val updatedDialog = dialog.copy(state = EditS10nNameDialogState.transition(dialog.state, EnteredName))
    updatedDialog.state match {
      case EditS10nNameDialogState.Finished => onFinish(draft)
      case state @ _ =>
        s10nRepo.update(draft).transact(xa) *>
          stateMessageService.getMessage(state)
    }
  }
}
