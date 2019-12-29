package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}

sealed trait Dialog

final case class CreateS10nDialog(
  state: CreateS10nDialogState,
  draft: SubscriptionDraft
) extends Dialog

final case class SettingsDialog(
  state: SettingsDialogState
) extends Dialog

final case class EditS10nNameDialog(
  state: EditS10nNameDialogState,
  draft: Subscription
) extends Dialog

final case class EditS10nAmountDialog(
  state: EditS10nAmountDialogState,
  draft: Subscription
) extends Dialog

final case class EditS10nOneTimeDialog(
  state: EditS10nOneTimeDialogState,
  draft: Subscription
) extends Dialog
