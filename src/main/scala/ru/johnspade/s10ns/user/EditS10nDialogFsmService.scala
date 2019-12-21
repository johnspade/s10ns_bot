package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{S10nsListMessageService, StateMessageService, Subscription, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.telegram.{DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.user.EditS10nNameDialogEvent.EnteredName

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    def onFinish(draft: Subscription) = {
      val replyOpt = s10nRepo.update(draft)
        .flatMap(_.traverse { s10n =>
          dialogEngine.reset(user, EditS10nNameDialogState.Finished.message)
            .map((_, s10n))
        })
        .transact(xa)
      for {
        reply <- replyOpt
        replies <- reply.traverse { p =>
          s10nsListMessageService.createSubscriptionMessage(user, p._2, PageNumber(0)).map(List(p._1, _))
        }
      } yield replies.getOrElse(singleTextMessage(Errors.notFound))
    }

    val draft = dialog.draft.copy(name = name)
    val updatedDialog = dialog.copy(state = EditS10nNameDialogState.transition(dialog.state, EnteredName))
    updatedDialog.state match {
      case EditS10nNameDialogState.Finished => onFinish(draft)
      case state @ _ =>
        s10nRepo.update(draft).transact(xa) *>
          stateMessageService.getMessage(state).map(List(_))
    }
  }
}
