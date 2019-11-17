package ru.johnspade.s10ns.user

import enumeratum._

import scala.collection.immutable.IndexedSeq

sealed abstract class SubscriptionDialogState(val message: String) extends EnumEntry

object SubscriptionDialogState extends Enum[SubscriptionDialogState] with DoobieEnum[SubscriptionDialogState] {
  case object Name extends SubscriptionDialogState("Name:")
  case object Currency extends SubscriptionDialogState("Currency:")
  case object Amount extends SubscriptionDialogState("Amount:")
  case object IsOneTime extends SubscriptionDialogState("Recurring/one time:")
  case object BillingPeriodUnit extends SubscriptionDialogState("Billing period unit:")
  case object BillingPeriodDuration extends SubscriptionDialogState("Billing period duration:")
  case object FirstPaymentDate extends SubscriptionDialogState("First payment date:")
  case object Finished extends SubscriptionDialogState("Subscription created.")

  override val values: IndexedSeq[SubscriptionDialogState] = findValues

  def transition(state: SubscriptionDialogState, event: SubscriptionDialogEvent): SubscriptionDialogState = {
    import ru.johnspade.s10ns.user.SubscriptionDialogEvent._

    val e = event
    state match {
      case Currency =>
        e match {
          case ChosenCurrency => Name
          case _ => state
        }
      case Name =>
        e match {
          case EnteredName => Amount
          case _ => state
        }
      case Amount =>
        e match {
          case EnteredAmount => IsOneTime
          case _ => state
        }
      case IsOneTime =>
        e match {
          case ChosenOneTime => FirstPaymentDate
          case ChosenRecurring => BillingPeriodUnit
          case _ => state
        }
      case BillingPeriodUnit =>
        e match {
          case ChosenBillingPeriodUnit => BillingPeriodDuration
          case _ => state
        }
      case BillingPeriodDuration =>
        e match {
          case EnteredBillingPeriodDuration => FirstPaymentDate
          case _ => state
        }
      case FirstPaymentDate =>
        e match {
          case ChosenFirstPaymentDate => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait SubscriptionDialogEvent extends EnumEntry

object SubscriptionDialogEvent extends Enum[SubscriptionDialogEvent] {
  case object EnteredName extends SubscriptionDialogEvent
  case object ChosenCurrency extends SubscriptionDialogEvent
  case object EnteredAmount extends SubscriptionDialogEvent
  case object ChosenOneTime extends SubscriptionDialogEvent
  case object ChosenRecurring extends SubscriptionDialogEvent
  case object EnteredBillingPeriodDuration extends SubscriptionDialogEvent
  case object ChosenBillingPeriodUnit extends SubscriptionDialogEvent
  case object ChosenFirstPaymentDate extends SubscriptionDialogEvent

  override def values: IndexedSeq[SubscriptionDialogEvent] = findValues
}
