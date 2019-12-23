package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{S10nsListMessageService, StateMessageService, Subscription, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.telegram.{DialogEngine, ReplyMessage}
import ru.johnspade.s10ns.user.EditS10nAmountDialogEvent.{ChosenCurrency, EnteredAmount}
import ru.johnspade.s10ns.user.EditS10nNameDialogEvent.EnteredName

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(name = name)
    val updatedDialog = dialog.copy(state = EditS10nNameDialogState.transition(dialog.state, EnteredName))
    updatedDialog.state match {
      case EditS10nNameDialogState.Finished => onFinish(user, draft, EditS10nNameDialogState.Finished.message)
      case state @ _ =>
        s10nRepo.update(draft).transact(xa) *>
          stateMessageService.getMessage(state).map(List(_))
    }
  }

  def saveCurrency(user: User, dialog: EditS10nAmountDialog, currency: CurrencyUnit): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(amount = Money.zero(currency))
    transitionEditS10nAmountDialogState(user, draft, dialog, ChosenCurrency)
  }

  def saveAmount(user: User, dialog: EditS10nAmountDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val draft = dialog.draft.copy(amount = Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transitionEditS10nAmountDialogState(user, draft, dialog, EnteredAmount)
  }

  private def transitionEditS10nAmountDialogState(
    user: User,
    draft: Subscription,
    dialog: EditS10nAmountDialog,
    event: EditS10nAmountDialogEvent
  ) = {
    val updatedDialog = dialog.copy(state = EditS10nAmountDialogState.transition(dialog.state, event))
    updatedDialog.state match {
      case EditS10nAmountDialogState.Finished => onFinish(user, draft, EditS10nAmountDialogState.Finished.message)
      case state @ _ =>
        s10nRepo.update(draft).transact(xa) *>
          stateMessageService.getMessage(state).map(List(_))
    }
  }

  private def onFinish(user: User, draft: Subscription, message: String) = {
    val replyOpt = s10nRepo.update(draft)
      .flatMap(_.traverse { s10n =>
        dialogEngine.reset(user, message)
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

}
