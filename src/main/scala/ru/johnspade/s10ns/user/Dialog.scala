package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}

sealed abstract class Dialog { self =>
  type S <: StateWithMessage
  type E <: StateEvent

  def state: S
  def finished: S
  def transition(event: E): Dialog {
      type S = self.S
      type E = self.E
    }
}

final case class CreateS10nDialog(
  override val state: CreateS10nDialogState,
  draft: SubscriptionDraft
) extends Dialog {
  type S = CreateS10nDialogState
  type E = CreateS10nDialogEvent

  override val finished: CreateS10nDialogState = CreateS10nDialogState.Finished
  override def transition(event: CreateS10nDialogEvent): CreateS10nDialog =
    copy(state = CreateS10nDialogState.transition(state, event))
}

final case class SettingsDialog(
  override val state: SettingsDialogState
) extends Dialog {
  type S = SettingsDialogState
  type E = SettingsDialogEvent

  override val finished: SettingsDialogState = SettingsDialogState.Finished
  override def transition(event: SettingsDialogEvent): SettingsDialog =
    copy(state = SettingsDialogState.transition(state, event))
}

sealed trait EditS10nDialog extends Dialog {
  def draft: Subscription
}

final case class EditS10nNameDialog(
  override val state: EditS10nNameDialogState,
  override val draft: Subscription
) extends Dialog with EditS10nDialog {
  type S = EditS10nNameDialogState
  type E = EditS10nNameDialogEvent

  override val finished: EditS10nNameDialogState = EditS10nNameDialogState.Finished
  def transition(event: EditS10nNameDialogEvent): EditS10nNameDialog =
    copy(state = EditS10nNameDialogState.transition(state, event))
}

final case class EditS10nAmountDialog(
  override val state: EditS10nAmountDialogState,
  override val draft: Subscription
) extends Dialog with EditS10nDialog {
  type S = EditS10nAmountDialogState
  type E = EditS10nAmountDialogEvent

  override val finished: EditS10nAmountDialogState = EditS10nAmountDialogState.Finished
  override def transition(event: EditS10nAmountDialogEvent): EditS10nAmountDialog =
    copy(state = EditS10nAmountDialogState.transition(state, event))
}

final case class EditS10nOneTimeDialog(
  override val state: EditS10nOneTimeDialogState,
  override val draft: Subscription
) extends Dialog with EditS10nDialog {
  type S = EditS10nOneTimeDialogState
  type E = EditS10nOneTimeDialogEvent

  override val finished: EditS10nOneTimeDialogState = EditS10nOneTimeDialogState.Finished
  override def transition(event: EditS10nOneTimeDialogEvent): EditS10nOneTimeDialog =
    copy(state = EditS10nOneTimeDialogState.transition(state, event))
}

final case class EditS10nBillingPeriodDialog(
  override val state: EditS10nBillingPeriodDialogState,
  override val draft: Subscription
) extends Dialog with EditS10nDialog {
  type S = EditS10nBillingPeriodDialogState
  type E = EditS10nBillingPeriodEvent

  override val finished: EditS10nBillingPeriodDialogState = EditS10nBillingPeriodDialogState.Finished
  override def transition(event: EditS10nBillingPeriodEvent): EditS10nBillingPeriodDialog =
    copy(state = EditS10nBillingPeriodDialogState.transition(state, event))
}


