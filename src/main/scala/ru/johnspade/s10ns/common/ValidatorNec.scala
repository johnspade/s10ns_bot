package ru.johnspade.s10ns.common

import cats.data.ValidatedNec
import cats.implicits._
import org.joda.money.CurrencyUnit
import ru.johnspade.s10ns.subscription.{BillingPeriodDuration, SubscriptionName}

import scala.util.Try

class ValidatorNec {
  type ValidationResult[A] = ValidatedNec[Validation, A]

  def validateText(text: Option[String]): ValidationResult[String] =
    text.map(_.validNec).getOrElse(TextCannotBeEmpty.invalidNec)

  def validateNameLength(name: SubscriptionName): ValidationResult[SubscriptionName] =
    if (name.value.length <= 256) name.validNec else NameTooLong.invalidNec

  def validateCurrency(currency: String): ValidationResult[CurrencyUnit] =
    Try(CurrencyUnit.of(currency)).toEither.left.map(_ => UnknownCurrency).toValidatedNec

  def validateAmountString(amount: String): ValidationResult[BigDecimal] =
    Try(BigDecimal(amount)).toEither.left.map(_ => NotANumber).toValidatedNec

  def validateAmount(amount: BigDecimal): ValidationResult[BigDecimal] =
    if (amount > 0) amount.validNec else NumberMustBePositive.invalidNec

  def validateDurationString(duration: String): ValidationResult[Int] =
    Try(duration.toInt).toEither.left.map(_ => NotANumber).toValidatedNec

  def validateDuration(duration: BillingPeriodDuration): ValidationResult[BillingPeriodDuration] =
    if (duration.value > 0) duration.validNec else NumberMustBePositive.invalidNec
}

object ValidatorNec extends ValidatorNec
