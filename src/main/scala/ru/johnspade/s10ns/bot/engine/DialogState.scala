package ru.johnspade.s10ns.bot.engine

import telegramium.bots.KeyboardMarkup

trait DialogState {
  def message: String

  def markup: Option[KeyboardMarkup]
}
