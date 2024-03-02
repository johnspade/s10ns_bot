package ru.johnspade.s10ns.bot

import scala.util.Try

import cats.data.ValidatedNec
import cats.implicits._

import org.joda.money.CurrencyUnit

class ValidatorNec {
  type ValidationResult[A] = ValidatedNec[Validation, A]

  def validateText(text: Option[String]): ValidationResult[String] =
    text.map(_.validNec).getOrElse(TextCannotBeEmpty.invalidNec)

  def validateNameLength(name: String): ValidationResult[String] =
    if (name.length <= 256) name.validNec else NameTooLong.invalidNec

  def validateCurrency(currency: String): ValidationResult[CurrencyUnit] =
    Try(CurrencyUnit.of(currency)).toEither.left.map(_ => UnknownCurrency).toValidatedNec

  def validateAmountString(amount: String): ValidationResult[BigDecimal] =
    Try(BigDecimal(amount)).toEither.left.map(_ => NotANumber).toValidatedNec

  def validateAmount(amount: BigDecimal): ValidationResult[BigDecimal] =
    if (amount > 0) amount.validNec else NumberMustBePositive.invalidNec

  def validateDurationString(duration: String): ValidationResult[Int] =
    Try(duration.toInt).toEither.left.map(_ => NotANumber).toValidatedNec

  def validateDuration(duration: Int): ValidationResult[Int] =
    if (duration > 0) duration.validNec else NumberMustBePositive.invalidNec
}

object ValidatorNec extends ValidatorNec
