package ru.johnspade.s10ns.user

import cats.effect.Sync
import cats.implicits._
import com.softwaremill.quicklens._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.subscription.{BillingPeriod, S10nsListMessageService, StateMessageService, Subscription, SubscriptionRepository}
import ru.johnspade.s10ns.telegram.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.telegram.{DialogEngine, ReplyMessage}

class EditS10nDialogFsmService[F[_] : Sync](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository,
  private val s10nRepo: SubscriptionRepository,
  private val dialogEngine: DialogEngine[F]
)(private implicit val xa: Transactor[F]) {
  def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.name).setTo(name)
    transition(user, updatedDialog)(EditS10nNameDialogEvent.EnteredName, stateMessageService.getTextMessage)
  }

  def saveCurrency(user: User, dialog: EditS10nAmountDialog, currency: CurrencyUnit): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.zero(currency))
    transition(user, updatedDialog)(EditS10nAmountDialogEvent.ChosenCurrency, stateMessageService.getTextMessage)
  }

  def saveAmount(user: User, dialog: EditS10nAmountDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transition(user, updatedDialog)(EditS10nAmountDialogEvent.EnteredAmount, stateMessageService.getTextMessage)
  }

  def saveIsOneTime(user: User, dialog: EditS10nOneTimeDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]] = {
    val updatedDialog = dialog
      .modify(_.draft.oneTime).setTo(oneTime)
      .modify(_.draft.billingPeriod).setTo(if (oneTime) None else dialog.draft.billingPeriod)
    val event = if (oneTime) EditS10nOneTimeDialogEvent.ChosenOneTime
    else {
      if (dialog.draft.billingPeriod.isDefined)
        EditS10nOneTimeDialogEvent.ChosenRecurringWithPeriod
      else
        EditS10nOneTimeDialogEvent.ChosenRecurringWithoutPeriod
    }
    transition(user, updatedDialog)(event, stateMessageService.getMessage)
  }

  def saveBillingPeriodUnit(user: User, dialog: EditS10nOneTimeDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod.some)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenBillingPeriodUnit, stateMessageService.getMessage)
  }

  def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
    val billingPeriod = dialog.draft
      .billingPeriod
      .map { period =>
        BillingPeriod(
          unit = period.unit,
          duration = duration
        )
      }
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenBillingPeriodDuration, stateMessageService.getMessage)
  }

  def saveBillingPeriodUnit(user: User, dialog: EditS10nBillingPeriodDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog
      .modify(_.draft.billingPeriod).setTo(billingPeriod.some)
      .modify(_.draft.oneTime).setTo(OneTimeSubscription(false))
    transition(user, updatedDialog)(EditS10nBillingPeriodEvent.ChosenBillingPeriodUnit, stateMessageService.getMessage)
  }

  def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
    val billingPeriod = dialog.draft
      .billingPeriod
      .map { period =>
        BillingPeriod(
          unit = period.unit,
          duration = duration
        )
      }
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod)
    transition(user, updatedDialog)(EditS10nBillingPeriodEvent.ChosenBillingPeriodDuration, stateMessageService.getMessage)
  }

  private def transition(user: User, dialog: EditS10nDialog with Dialog)
    (event: dialog.E, getMessage: dialog.S => F[ReplyMessage]): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.transition(event)
    if (updatedDialog.state == dialog.finished)
      onFinish(user, dialog.draft, dialog.finished.message)
    else {
      val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
      userRepo.createOrUpdate(userWithUpdatedDialog).transact(xa) *>
        getMessage(updatedDialog.state).map(List(_))
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
