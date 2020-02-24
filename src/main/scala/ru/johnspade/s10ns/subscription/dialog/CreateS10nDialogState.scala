package ru.johnspade.s10ns.subscription.dialog

import cats.syntax.option._
import enumeratum._
import ru.johnspade.s10ns.bot.engine.{DialogState, StateEvent}
import ru.johnspade.s10ns.bot.{Markup, Messages}
import telegramium.bots.KeyboardMarkup

import scala.collection.immutable.IndexedSeq

sealed abstract class CreateS10nDialogState(override val message: String, override val markup: Option[KeyboardMarkup])
  extends EnumEntry with DialogState

object CreateS10nDialogState
  extends Enum[CreateS10nDialogState]
    with CirceEnum[CreateS10nDialogState] {
  case object Name extends CreateS10nDialogState(Messages.Name, None)
  case object Currency extends CreateS10nDialogState(Messages.Currency, Markup.CurrencyReplyMarkup.some)
  case object Amount extends CreateS10nDialogState(Messages.Amount, None)
  case object IsOneTime extends CreateS10nDialogState(Messages.IsOneTime, Markup.isOneTimeReplyMarkup("Skip").some)
  case object BillingPeriodUnit extends CreateS10nDialogState(Messages.BillingPeriodUnit, Markup.BillingPeriodUnitReplyMarkup.some)
  case object BillingPeriodDuration extends CreateS10nDialogState(Messages.BillingPeriodDuration, None)
  case object FirstPaymentDate extends CreateS10nDialogState(Messages.FirstPaymentDate, None)
  case object Finished extends CreateS10nDialogState(Messages.S10nSaved, None)

  override val values: IndexedSeq[CreateS10nDialogState] = findValues

  def transition(state: CreateS10nDialogState, event: CreateS10nDialogEvent): CreateS10nDialogState = {
    import CreateS10nDialogEvent._

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
          case SkippedIsOneTime => Finished
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
          case ChosenFirstPaymentDate | SkippedFirstPaymentDate => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait CreateS10nDialogEvent extends EnumEntry with StateEvent

object CreateS10nDialogEvent extends Enum[CreateS10nDialogEvent] with CirceEnum[CreateS10nDialogEvent] {
  case object EnteredName extends CreateS10nDialogEvent
  case object ChosenCurrency extends CreateS10nDialogEvent
  case object EnteredAmount extends CreateS10nDialogEvent
  case object SkippedIsOneTime extends CreateS10nDialogEvent
  case object ChosenOneTime extends CreateS10nDialogEvent
  case object ChosenRecurring extends CreateS10nDialogEvent
  case object EnteredBillingPeriodDuration extends CreateS10nDialogEvent
  case object ChosenBillingPeriodUnit extends CreateS10nDialogEvent
  case object SkippedFirstPaymentDate extends CreateS10nDialogEvent
  case object ChosenFirstPaymentDate extends CreateS10nDialogEvent

  override def values: IndexedSeq[CreateS10nDialogEvent] = findValues
}
