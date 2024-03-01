package ru.johnspade.s10ns.settings

import scala.collection.immutable.IndexedSeq

import cats.syntax.option._

import enumeratum._
import telegramium.bots.KeyboardMarkup

import ru.johnspade.s10ns.bot.Markup
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.engine.DialogState
import ru.johnspade.s10ns.bot.engine.StateEvent

sealed abstract class SettingsDialogState(override val message: String, override val markup: Option[KeyboardMarkup])
  extends EnumEntry with DialogState

object SettingsDialogState
  extends Enum[SettingsDialogState]
    with CirceEnum[SettingsDialogState] {
  case object DefaultCurrency extends SettingsDialogState(Messages.Currency, Markup.CurrencyReplyMarkup.some)
  case object Finished extends SettingsDialogState("Default currency set.", None)

  override val values: IndexedSeq[SettingsDialogState] = findValues

  def transition(state: SettingsDialogState, event: SettingsDialogEvent): SettingsDialogState = {
    import SettingsDialogEvent._

    val e = event
    state match {
      case DefaultCurrency =>
        e match {
          case ChosenDefaultCurrency => Finished
          case _ => state
        }
      case _ => state
    }
  }
}

sealed trait SettingsDialogEvent extends EnumEntry with StateEvent

object SettingsDialogEvent extends Enum[SettingsDialogEvent] with CirceEnum[SettingsDialogEvent] {
  case object ChosenDefaultCurrency extends SettingsDialogEvent
  override def values: IndexedSeq[SettingsDialogEvent] = findValues
}
