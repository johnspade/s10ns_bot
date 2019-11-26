package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.common.tags._
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{S10nsListMessageService, Subscription, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.{ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.user.EditS10nNameDialogEvent.EnteredName

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository
)(private implicit val xa: Transactor[F]) {
  def saveName(user: User, name: SubscriptionName): F[ReplyMessage] = {
    def transition() =
      user.dialogType.flatMap {
        case DialogType.EditS10nName =>
          user.editS10nNameDialogState.map(EditS10nNameDialogState.transition(_, EnteredName))
        case _ => Option.empty[EditS10nNameDialogState]
      }

    def onFinish(draft: Subscription) = {
      val userWithNewState = user.copy(dialogType = None, subscriptionDraft = None)
      for {
        s10nOpt <- userRepo.update(userWithNewState).productR(s10nRepo.update(draft)).transact(xa)
        replyOpt <- s10nOpt.traverse { s10n =>
          s10nsListMessageService.createSubscriptionMessage(user, s10n, PageNumber(0))
        }
      } yield replyOpt.toRight(Errors.notFound)
    }

    user.existingS10nDraft
      .traverse { s10nDraft =>
        val draft = s10nDraft.copy(name = name)
        val newState = transition()
        newState match {
          case Some(EditS10nNameDialogState.Finished) =>
            onFinish(draft)
          case _ =>
            Sync[F].pure(Either.left[String, ReplyMessage](Errors.default))
        }
      }
      .map {
        _.toRight(Errors.default).flatten.left.map(ReplyMessage(_)).merge
      }
  }
}
