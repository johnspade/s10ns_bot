package ru.johnspade.s10ns.user

import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.subscription.SubscriptionDraft

final case class UserId(value: Long) extends AnyVal
final case class FirstName(value: String) extends AnyVal
final case class LastName(value: String) extends AnyVal
final case class Username(value: String) extends AnyVal
final case class ChatId(value: Long) extends AnyVal

case class User(
  id: UserId,
  firstName: FirstName,
  lastName: Option[LastName],
  username: Option[Username],
  chatId: Option[ChatId],
  defaultCurrency: CurrencyUnit = CurrencyUnit.EUR,
  dialogType: Option[DialogType] = None,
  subscriptionDialogState: Option[SubscriptionDialogState] = None,
  settingsDialogState: Option[SettingsDialogState] = None,
  subscriptionDraft: Option[SubscriptionDraft] = None
)
