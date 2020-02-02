package ru.johnspade.s10ns.subscription.service

import cats.{Applicative, Monad, ~>}
import cats.effect.Sync
import cats.implicits._
import com.softwaremill.quicklens._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.{EditS10nAmountDialog, EditS10nBillingPeriodDialog, EditS10nCurrencyDialog, EditS10nDialog, EditS10nFirstPaymentDateDialog, EditS10nNameDialog, EditS10nOneTimeDialog, Errors, StateMessageService}
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, ReplyMessage, TransactionalDialogEngine}
import ru.johnspade.s10ns.subscription.tags._
import ru.johnspade.s10ns.bot.engine.TelegramOps.singleTextMessage
import ru.johnspade.s10ns.subscription.dialog.{EditS10nAmountDialogEvent, EditS10nBillingPeriodEvent, EditS10nCurrencyDialogEvent, EditS10nFirstPaymentDateDialogEvent, EditS10nNameDialogEvent, EditS10nOneTimeDialogEvent}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit, Subscription}
import ru.johnspade.s10ns.user.{User, UserRepository}

class DefaultEditS10nDialogFsmService[F[_] : Sync, D[_] : Monad](
  private val s10nsListMessageService: S10nsListMessageService[F],
  private val stateMessageService: StateMessageService[F],
  private val userRepo: UserRepository[D],
  private val s10nRepo: SubscriptionRepository[D],
  private val dialogEngine: TransactionalDialogEngine[F, D]
)(private implicit val transact: D ~> F) extends EditS10nDialogFsmService[F] {
  override def saveName(user: User, dialog: EditS10nNameDialog, name: SubscriptionName): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.name).setTo(name)
    transition(user, updatedDialog)(EditS10nNameDialogEvent.EnteredName, stateMessageService.getTextMessage)
  }

  override def saveAmount(user: User, dialog: EditS10nAmountDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transition(user, updatedDialog)(EditS10nAmountDialogEvent.EnteredAmount, stateMessageService.getMessage)
  }

  override def saveCurrency(user: User, dialog: EditS10nCurrencyDialog, currency: CurrencyUnit): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.zero(currency))
    transition(user, updatedDialog)(EditS10nCurrencyDialogEvent.ChosenCurrency, stateMessageService.getMessage)
  }

  override def saveAmount(user: User, dialog: EditS10nCurrencyDialog, amount: BigDecimal): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.amount).setTo(Money.of(dialog.draft.amount.getCurrencyUnit, amount.bigDecimal))
    transition(user, updatedDialog)(EditS10nCurrencyDialogEvent.EnteredAmount, stateMessageService.getMessage)
  }

  override def removeIsOneTime(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] = {
    val updatedDialog = dialog
      .modify(_.draft.oneTime).setTo(None)
      .modify(_.draft.billingPeriod).setTo(None)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.RemovedIsOneTime, stateMessageService.getMessage)
  }

  override def saveIsOneTime(user: User, dialog: EditS10nOneTimeDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]] = {
    val updatedDialog = dialog
      .modify(_.draft.oneTime).setTo(oneTime.some)
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

  override def saveBillingPeriodUnit(user: User, dialog: EditS10nOneTimeDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod.some)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenBillingPeriodUnit, stateMessageService.getMessage)
  }

  override def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
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

  override def saveBillingPeriodUnit(user: User, dialog: EditS10nBillingPeriodDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog
      .modify(_.draft.billingPeriod).setTo(billingPeriod.some)
      .modify(_.draft.oneTime).setTo(OneTimeSubscription(false).some)
    transition(user, updatedDialog)(EditS10nBillingPeriodEvent.ChosenBillingPeriodUnit, stateMessageService.getMessage)
  }

  override def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
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

  override def removeFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.firstPaymentDate).setTo(None)
    transition(user, updatedDialog)(EditS10nFirstPaymentDateDialogEvent.RemovedFirstPaymentDate, stateMessageService.getMessage)
  }

  override def saveFirstPaymentDate(user: User, dialog: EditS10nFirstPaymentDateDialog, date: FirstPaymentDate): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.modify(_.draft.firstPaymentDate).setTo(date.some)
    transition(user, updatedDialog)(EditS10nFirstPaymentDateDialogEvent.ChosenFirstPaymentDate, stateMessageService.getMessage)
  }

  private def transition(user: User, dialog: EditS10nDialog)
    (event: dialog.E, getMessage: dialog.S => F[ReplyMessage]): F[List[ReplyMessage]] = {
    val updatedDialog = dialog.transition(event)
    if (updatedDialog.state == dialog.finished)
      onFinish(user, dialog.draft, dialog.finished.message)
    else {
      val userWithUpdatedDialog = user.copy(dialog = updatedDialog.some)
      transact(userRepo.createOrUpdate(userWithUpdatedDialog)) *>
        getMessage(updatedDialog.state).map(List(_))
    }
  }

  private def onFinish(user: User, draft: Subscription, message: String) = {
    val replyOpt = transact {
      s10nRepo.update(draft)
        .flatMap(_.traverse { s10n =>
          dialogEngine.reset(user, message)
            .map((_, s10n))
        })
    }
    for {
      reply <- replyOpt
      replies <- reply.traverse { p =>
        s10nsListMessageService.createSubscriptionMessage(user.defaultCurrency, p._2, PageNumber(0)).map(List(p._1, _))
      }
    } yield replies.getOrElse(singleTextMessage(Errors.NotFound))
  }
}
