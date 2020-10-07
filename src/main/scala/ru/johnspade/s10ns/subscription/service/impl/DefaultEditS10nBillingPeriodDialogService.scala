package ru.johnspade.s10ns.subscription.service.impl

import cats.syntax.option._
import cats.{Monad, ~>}
import com.softwaremill.quicklens._
import ru.johnspade.s10ns.bot.ValidatorNec._
import ru.johnspade.s10ns.bot.engine.{ReplyMessage, StateMessageService, TransactionalDialogEngine}
import ru.johnspade.s10ns.bot.{EditS10nBillingPeriod, EditS10nBillingPeriodDialog, PeriodUnit}
import ru.johnspade.s10ns.subscription.dialog.{EditS10nBillingPeriodDialogState, EditS10nBillingPeriodEvent}
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{EditS10nBillingPeriodDialogService, RepliesValidated, S10nsListMessageService}
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, OneTimeSubscription}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit}
import ru.johnspade.s10ns.user.{User, UserRepository}

class DefaultEditS10nBillingPeriodDialogService[F[_] : Monad, D[_] : Monad](
  s10nsListMessageService: S10nsListMessageService[F],
  stateMessageService: StateMessageService[F, EditS10nBillingPeriodDialogState],
  userRepo: UserRepository[D],
  s10nRepo: SubscriptionRepository[D],
  dialogEngine: TransactionalDialogEngine[F, D]
)(implicit transact: D ~> F)
  extends EditS10nDialogService[F, D, EditS10nBillingPeriodDialogState](
    s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
  ) with EditS10nBillingPeriodDialogService[F] {
  override def onEditS10nBillingPeriodCb(user: User, data: EditS10nBillingPeriod): F[List[ReplyMessage]] =
    onEditS10nDialogCb(
      user = user,
      s10nId = data.subscriptionId,
      state = EditS10nBillingPeriodDialogState.BillingPeriodUnit,
      createDialog =
        s10n => EditS10nBillingPeriodDialog(
          EditS10nBillingPeriodDialogState.BillingPeriodUnit,
          s10n
        )
    )

  override def saveBillingPeriodUnit(data: PeriodUnit, user: User, dialog: EditS10nBillingPeriodDialog): F[List[ReplyMessage]] =
    saveBillingPeriodUnit(user, dialog, data.unit)

  override def saveBillingPeriodDuration(user: User, dialog: EditS10nBillingPeriodDialog, text: Option[String]): F[RepliesValidated] =
    validateText(text)
      .andThen(validateDurationString)
      .andThen(duration => validateDuration(BillingPeriodDuration(duration)))
      .traverse(saveBillingPeriodDuration(user, dialog, _))

  private def saveBillingPeriodUnit(user: User, dialog: EditS10nBillingPeriodDialog, unit: BillingPeriodUnit): F[List[ReplyMessage]] = {
    val billingPeriod = BillingPeriod(
      unit = unit,
      duration = BillingPeriodDuration(1)
    )
    val updatedDialog = dialog
      .modify(_.draft.billingPeriod).setTo(billingPeriod.some)
      .modify(_.draft.oneTime).setTo(OneTimeSubscription(false).some)
    transition(user, updatedDialog)(EditS10nBillingPeriodEvent.ChosenBillingPeriodUnit)
  }

  private def saveBillingPeriodDuration(
    user: User,
    dialog: EditS10nBillingPeriodDialog,
    duration: BillingPeriodDuration
  ): F[List[ReplyMessage]] = {
    val billingPeriod = dialog.draft
      .billingPeriod
      .map { period =>
        BillingPeriod(
          unit = period.unit,
          duration = duration
        )
      }
    val updatedDialog = dialog.modify(_.draft.billingPeriod).setTo(billingPeriod)
    transition(user, updatedDialog)(EditS10nBillingPeriodEvent.ChosenBillingPeriodDuration)
  }
}
