package ru.johnspade.s10ns.user

import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags._

case class User(
  id: UserId,
  firstName: FirstName,
  lastName: Option[LastName],
  username: Option[Username],
  chatId: Option[ChatId],
  defaultCurrency: CurrencyUnit = CurrencyUnit.EUR,
  dialogType: Option[DialogType] = None,
  subscriptionDialogState: Option[CreateS10nDialogState] = None,
  settingsDialogState: Option[SettingsDialogState] = None,
  editS10nNameDialogState: Option[EditS10nNameDialogState] = None,
  subscriptionDraft: Option[SubscriptionDraft] = None,
  existingS10nDraft: Option[Subscription] = None
)
