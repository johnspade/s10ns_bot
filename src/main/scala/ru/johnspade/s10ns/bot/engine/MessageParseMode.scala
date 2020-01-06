package ru.johnspade.s10ns.bot.engine

import enumeratum.values.{StringEnum, StringEnumEntry}

import scala.collection.immutable.IndexedSeq

sealed abstract class MessageParseMode(override val value: String) extends StringEnumEntry

object MessageParseMode extends StringEnum[MessageParseMode] {
  case object Markdown extends MessageParseMode("Markdown")
  case object Html extends MessageParseMode("HTML")

  override def values: IndexedSeq[MessageParseMode] = findValues
}
