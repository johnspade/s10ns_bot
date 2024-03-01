package ru.johnspade.s10ns.subscription.dialog

import scala.collection.immutable.IndexedSeq

import cats.syntax.option._

import enumeratum._
import telegramium.bots.KeyboardMarkup

import ru.johnspade.s10ns.bot.Markup
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.DialogState
import ru.johnspade.s10ns.bot.engine.StateEvent


sealed abstract class EditS10nBillingPeriodDialogState(
  override val message: String,
  override val markup: Option[KeyboardMarkup]
)
  extends EnumEntry with DialogState

object EditS10nBillingPeriodDialogState
  extends Enum[EditS10nBillingPeriodDialogState] with CirceEnum[EditS10nBillingPeriodDialogState] {
  case object BillingPeriodUnit extends EditS10nBillingPeriodDialogState(
    Messages.BillingPeriodUnit,
    Markup.BillingPeriodUnitReplyMarkup.some
  )
  case object BillingPeriodDuration extends EditS10nBillingPeriodDialogState(Messages.BillingPeriodDuration, None)
  case object Finished extends EditS10nBillingPeriodDialogState(Messages.S10nSaved, None)

  override def values: IndexedSeq[EditS10nBillingPeriodDialogState] = findValues

  def transition(state: EditS10nBillingPeriodDialogState, event: EditS10nBillingPeriodEvent): EditS10nBillingPeriodDialogState = {
    import EditS10nBillingPeriodEvent._

    val e = event
    state match {
      case BillingPeriodUnit =>
        e match {
          case ChosenBillingPeriodUnit => BillingPeriodDuration
          case _ => state
        }
      case BillingPeriodDuration =>
        e match {
          case ChosenBillingPeriodDuration => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait EditS10nBillingPeriodEvent extends EnumEntry with StateEvent

object EditS10nBillingPeriodEvent extends Enum[EditS10nBillingPeriodEvent] with CirceEnum[EditS10nBillingPeriodEvent] {
  case object ChosenBillingPeriodUnit extends EditS10nBillingPeriodEvent
  case object ChosenBillingPeriodDuration extends EditS10nBillingPeriodEvent

  override def values: IndexedSeq[EditS10nBillingPeriodEvent] = findValues
}

