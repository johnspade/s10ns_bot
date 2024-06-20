package ru.johnspade.s10ns.bot

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import scala.util.Try

import ru.johnspade.csv3s.codecs.RowDecoder
import ru.johnspade.csv3s.codecs.RowEncoder
import ru.johnspade.csv3s.codecs.StringDecoder
import ru.johnspade.csv3s.codecs.StringEncoder
import ru.johnspade.csv3s.codecs.instances.given
import ru.johnspade.csv3s.parser.CsvParser
import ru.johnspade.csv3s.parser.*
import ru.johnspade.csv3s.printer.CsvPrinter
import ru.johnspade.tgbot.callbackdata.named.MagnoliaRowDecoder
import ru.johnspade.tgbot.callbackdata.named.MagnoliaRowEncoder
import ru.johnspade.tgbot.callbackqueries.DecodeError
import ru.johnspade.tgbot.callbackqueries.DecodeFailure
import ru.johnspade.tgbot.callbackqueries.ParseError

import ru.johnspade.s10ns.bot.CbData._
import ru.johnspade.s10ns.subscription.BillingPeriodUnit

sealed abstract class CbData extends Product with Serializable {
  import CbData.{encoder, csvPrinter}

  def toCsv: String = csvPrinter.print(encoder.encode(this))

  def print: String = ""
}

sealed trait StartsDialog extends CbData

sealed trait Skip extends CbData {
  override def print: String = "Skipped"
}

case object Ignore extends CbData

final case class S10ns(page: Int) extends CbData

final case class S10n(subscriptionId: Long, page: Int) extends CbData

final case class PeriodUnit(unit: BillingPeriodUnit) extends CbData {
  override def print: String = unit.chronoUnit.toString
}

case object SkipIsOneTime extends CbData with Skip

final case class OneTime(oneTime: Boolean) extends CbData {
  override def print: String = if (oneTime) "One time" else "Recurring"
}

case object EveryMonth extends CbData {
  override def print: String = "Every month"
}

final case class Calendar(date: LocalDate) extends CbData

final case class Years(yearMonth: YearMonth) extends CbData

final case class Months(year: Int) extends CbData

final case class FirstPayment(date: LocalDate) extends CbData {
  override def print: String = DateTimeFormatter.ISO_DATE.format(date)
}

case object DropFirstPayment extends CbData with Skip

final case class RemoveS10n(subscriptionId: Long, page: Int) extends CbData

final case class EditS10n(subscriptionId: Long, page: Int) extends CbData

final case class EditS10nName(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nCurrency(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nAmount(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nOneTime(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nBillingPeriod(subscriptionId: Long) extends CbData with StartsDialog

final case class EditS10nFirstPaymentDate(subscriptionId: Long) extends CbData with StartsDialog

case object DefCurrency extends CbData with StartsDialog

final case class S10nsPeriod(period: BillingPeriodUnit, page: Int) extends CbData

final case class Notify(subscriptionId: Long, enable: Boolean, page: Int) extends CbData

case object NotifyByDefault extends CbData

object CbData {
  given StringEncoder[BillingPeriodUnit] = _.entryName
  given StringDecoder[BillingPeriodUnit] = s =>
    BillingPeriodUnit.withNameOption(s).toRight(StringDecoder.typeError(s, "BillingPeriodUnit"))

  given StringEncoder[LocalDate] = _.format(DateTimeFormatter.ISO_LOCAL_DATE)
  given StringDecoder[LocalDate] = s =>
    Try(LocalDate.parse(s)).toEither.left.map { e =>
      ru.johnspade.csv3s.codecs.DecodeError.TypeError(s"$s is not valid LocalDate: ${e.getMessage()}")
    }

  given StringEncoder[YearMonth] = _.toString()
  given StringDecoder[YearMonth] = s =>
    Try(YearMonth.parse(s)).toEither.left.map { e =>
      ru.johnspade.csv3s.codecs.DecodeError.TypeError(s"$s is not valid YearMonth: ${e.getMessage()}")
    }

  given encoder: RowEncoder[CbData] = MagnoliaRowEncoder.derived
  given decoder: RowDecoder[CbData] = MagnoliaRowDecoder.derived

  val Separator: Char        = '\u001D'
  private val csvParser      = new CsvParser(Separator)
  val csvPrinter: CsvPrinter = CsvPrinter.withSeparator(Separator)

  def decode(csv: String): Either[DecodeFailure, CbData] =
    parseRow(csv, csvParser).left
      .map(e => ParseError(e.toString))
      .flatMap(decoder.decode(_).left.map(e => DecodeError(e.getMessage)))
}
