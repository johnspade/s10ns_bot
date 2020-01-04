package ru.johnspade.s10ns.bot

sealed trait Validation {
  def errorMessage: String
}

case object TextCannotBeEmpty extends Validation {
  override def errorMessage: String = "Text cannot be empty."
}

case object NameTooLong extends Validation {
  override def errorMessage: String = "Name cannot be longer than 256 symbols."
}

case object UnknownCurrency extends Validation {
  override def errorMessage: String = "Unknown currency."
}

case object NotANumber extends Validation {
  override def errorMessage: String = "Not a number."
}

case object NumberMustBePositive extends Validation {
  override def errorMessage: String = "Number must be positive."
}
