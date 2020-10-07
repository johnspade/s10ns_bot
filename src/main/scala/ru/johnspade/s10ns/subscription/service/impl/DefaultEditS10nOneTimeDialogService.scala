package ru.johnspade.s10ns.subscription.service.impl

import cats.syntax.option._
import cats.{Monad, ~>}
import com.softwaremill.quicklens._
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.{ReplyMessage, StateMessageService, TransactionalDialogEngine}
import ru.johnspade.s10ns.bot.{EditS10nOneTime, EditS10nOneTimeDialog, OneTime, PeriodUnit}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nOneTimeDialogEvent, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{EditS10nOneTimeDialogService, RepliesValidated, S10nsListMessageService}
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, OneTimeSubscription}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit}
import ru.johnspade.s10ns.user.{User, UserRepository}

class DefaultEditS10nOneTimeDialogService[F[_] : Monad, D[_] : Monad](
  s10nsListMessageService: S10nsListMessageService[F],
  stateMessageService: StateMessageService[F, EditS10nOneTimeDialogState],
  userRepo: UserRepository[D],
  s10nRepo: SubscriptionRepository[D],
  dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
  extends EditS10nDialogService[F, D, EditS10nOneTimeDialogState](
    s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
  ) with EditS10nOneTimeDialogService[F] {
  override def saveEveryMonth(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] =
    saveEveryMonthTransition(user, dialog)

  override def removeIsOneTime(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] =
    removeIsOneTimeTransition(user, dialog)

  override def saveIsOneTime(data: OneTime, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] =
    saveIsOneTimeTransition(user, dialog, data.oneTime)

  override def onEditS10nOneTimeCb(user: User, data: EditS10nOneTime): F[List[ReplyMessage]] =
    onEditS10nDialogCb(
      user = user,
      s10nId = data.subscriptionId,
      state = EditS10nOneTimeDialogState.IsOneTime,
      createDialog =
        s10n => EditS10nOneTimeDialog(
          EditS10nOneTimeDialogState.IsOneTime,
          s10n
        )
    )

  override def saveBillingPeriodUnit(data: PeriodUnit, user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] = saveBillingPeriodUnitTransition(user, dialog, data.unit)

  override def saveBillingPeriodDuration(user: User, dialog: EditS10nOneTimeDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateDurationString)
      .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
      .traverse(saveBillingPeriodDurationTransition(user, dialog, _))

  private def saveEveryMonthTransition(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] = {
    val everyMonth = BillingPeriod(unit = BillingPeriodUnit.Month, duration = BillingPeriodDuration(1)).some
    val updatedDialog = dialog
      .modify(_.draft.oneTime).setTo(OneTimeSubscription(false).some)
      .modify(_.draft.billingPeriod).setTo(everyMonth)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenEveryMonth)
  }

  private def removeIsOneTimeTransition(user: User, dialog: EditS10nOneTimeDialog): F[List[ReplyMessage]] = {
    val updatedDialog = dialog
      .modify(_.draft.oneTime).setTo(None)
      .modify(_.draft.billingPeriod).setTo(None)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.RemovedIsOneTime)
  }

  private def saveIsOneTimeTransition(user: User, dialog: EditS10nOneTimeDialog, oneTime: OneTimeSubscription): F[List[ReplyMessage]] = {
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
    transition(user, updatedDialog)(event)
  }

  private def saveBillingPeriodUnitTransition(user: User, dialog: EditS10nOneTimeDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod.some)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenBillingPeriodUnit)
  }

  private def saveBillingPeriodDurationTransition(user: User, dialog: EditS10nOneTimeDialog, duration: BillingPeriodDuration): F[List[ReplyMessage]] = {
    val billingPeriod = dialog.draft
      .billingPeriod
      .map { period =>
        BillingPeriod(
          unit = period.unit,
          duration = duration
        )
      }
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod)
    transition(user, updatedDialog)(EditS10nOneTimeDialogEvent.ChosenBillingPeriodDuration)
  }
}
