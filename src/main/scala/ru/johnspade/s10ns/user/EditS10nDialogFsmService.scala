package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.s10ns.common.{Errors, PageNumber}
import ru.johnspade.s10ns.subscription.{S10nsListMessageService, SubscriptionName, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.{ReplyMessage, StateMessageService}
import ru.johnspade.s10ns.user.EditS10nNameDialogEvent.EnteredName

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val xa: Transactor[F]
) {
  // todo refactor
  def saveName(user: User, name: SubscriptionName): F[ReplyMessage] =
    getSubscriptionDraft(user)
      .traverse { d =>
        val draft = d.copy(name = name)
        val newState = user.dialogType.flatMap {
          case DialogType.EditS10nName =>
            user.editS10nNameDialogState.map(EditS10nNameDialogState.transition(_, EnteredName))
          case _ => Option.empty[EditS10nNameDialogState]
        }
        newState match {
          case Some(EditS10nNameDialogState.Finished) =>
            val userWithNewState = user.copy(dialogType = None, subscriptionDraft = None)
            userRepo.update(userWithNewState)
              .productR(s10nRepo.update(draft))
              .transact(xa)
              .flatMap {
                _.traverse { s =>
                  s10nsListMessageService.createSubscriptionMessage(user.id, s.id, PageNumber(0))
                }
              }
              .map {
                _.toRight(Errors.notFound).flatten
              }
          case _ =>
            Sync[F].pure(Either.left[String, ReplyMessage](Errors.default))
        }
      }
      .map {
        _.toRight(Errors.default).flatten.left.map(ReplyMessage(_)).merge
      }

  private def getSubscriptionDraft(user: User) = user.existingS10nDraft
}
