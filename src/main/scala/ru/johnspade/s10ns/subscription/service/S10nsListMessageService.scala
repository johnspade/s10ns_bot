package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import org.joda.money.format.MoneyFormatterBuilder
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.{EditS10n, EditS10nAmount, EditS10nBillingPeriod, EditS10nFirstPaymentDate, EditS10nName, EditS10nOneTime, MoneyService, RemoveS10n, S10n, S10ns}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.user.User
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup, MarkupInlineKeyboard}

class S10nsListMessageService[F[_] : Sync](
  private val moneyService: MoneyService[F]
) {
  private val DefaultPageSize = 10
  private val MoneyFormatter =
    new MoneyFormatterBuilder()
      .appendAmount()
      .appendLiteral(" ")
      .appendCurrencySymbolLocalized()
      .toFormatter

  def createSubscriptionsPage(subscriptions: List[Subscription], page: PageNumber, defaultCurrency: CurrencyUnit):
  F[ReplyMessage] = {
    def createText(indexedSubscriptions: List[(Subscription, Int)], sum: Option[Money]) = {
      val sumString = sum.map(MoneyFormatter.print).getOrElse("")
      val list = indexedSubscriptions
        .map {
          case (s, i) => s"$i. ${s.name} – ${MoneyFormatter.print(s.amount)}"
        }
        .mkString("\n")
      s"$sumString\n$list"
    }

    def createSubscriptionButtons(indexedSubscriptions: List[(Subscription, Int)]): List[List[InlineKeyboardButton]] =
      indexedSubscriptions
        .grouped(5)
        .toList
        .map(_.map {
          case (s, i) => inlineKeyboardButton(i.toString, S10n(s.id, page))
        })

    def createNavButtons(indexedSubscriptions: List[(Subscription, Int)]): List[InlineKeyboardButton] = {
      val pageLastElementNumber = DefaultPageSize * (page + 1)
      val leftButton = inlineKeyboardButton("⬅", S10ns(PageNumber(page - 1)))
      val rightButton = inlineKeyboardButton("➡", S10ns(PageNumber(page + 1)))
      List(
        (pageLastElementNumber > DefaultPageSize, leftButton),
        (pageLastElementNumber < indexedSubscriptions.size, rightButton)
      )
        .filter(_._1)
        .map(_._2)
    }

    def createReplyMessage(subscriptions: List[Subscription]): F[ReplyMessage] =
      moneyService.sum(subscriptions, defaultCurrency).map { sum =>
        val from = page * DefaultPageSize
        val until = from + DefaultPageSize
        val indexedS10ns = subscriptions.zipWithIndex.map {
          case (s, i) => (s, i + 1)
        }
        val indexedS10nsPage = indexedS10ns.slice(from, until)
        val text = createText(indexedS10nsPage, sum)
        val subscriptionButtons = createSubscriptionButtons(indexedS10nsPage)
        val arrowButtons = createNavButtons(indexedS10ns)
        ReplyMessage(text, MarkupInlineKeyboard(InlineKeyboardMarkup(subscriptionButtons :+ arrowButtons)).some)
      }

    createReplyMessage(subscriptions)
  }

  def createSubscriptionMessage(user: User, s10n: Subscription, page: PageNumber): F[ReplyMessage] = {
    val name = s10n.name
    val amount = MoneyFormatter.print(s10n.amount)
    val amountInStandardCurrency = {
      val converted =
        if (s10n.amount.getCurrencyUnit == user.defaultCurrency) Monad[F].pure(Option.empty[Money])
        else moneyService.convert(s10n.amount, user.defaultCurrency)
      converted.map(_.map(MoneyFormatter.print))
    }
    val billingPeriod = s10n.billingPeriod.map { period =>
      val number = if (period.duration == 1) ""
      else s" ${period.duration}"
      val unitName = period.unit.toString.toLowerCase.reverse.replaceFirst("s", "").reverse
      s"Billing period: every$number $unitName"
    }
    val nextPayment = Option.empty[String] // todo
    val firstPaymentDate = s10n.firstPaymentDate.map { date =>
      s"First payment: ${DateTimeFormatter.ISO_DATE.format(date)}"
    }
    val paidInTotal = Option.empty[String] // todo
    amountInStandardCurrency.map { amountStd =>
      val text = Seq(name.some, amount.some, amountStd, billingPeriod, nextPayment, firstPaymentDate, paidInTotal)
        .flatten
        .mkString("\n")
      val editButton =
        inlineKeyboardButton("Edit", EditS10n(s10n.id, page))
      val removeButton =
        inlineKeyboardButton("Remove", RemoveS10n(s10n.id, page))
      val backButton = inlineKeyboardButton("List", S10ns(page))
      val buttonsList = List(List(editButton), List(removeButton), List(backButton))
      ReplyMessage(text, MarkupInlineKeyboard(InlineKeyboardMarkup(buttonsList)).some)
    }
  }

  def createEditS10nMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup = {
    val nameButton = inlineKeyboardButton("Edit name", EditS10nName(s10n.id))
    val currencyButton = inlineKeyboardButton("Edit currency", EditS10nAmount(s10n.id))
    val oneTimeButton = inlineKeyboardButton("Recurring/one time", EditS10nOneTime(s10n.id))
    val billingPeriodButton = if (s10n.oneTime.getOrElse(false)) List.empty
    else List(inlineKeyboardButton("Edit billing period", EditS10nBillingPeriod(s10n.id)))
    val firstPaymentDateButton = inlineKeyboardButton("Edit first payment date", EditS10nFirstPaymentDate(s10n.id))
    val backButton = inlineKeyboardButton("Back", S10n(s10n.id, page))
    InlineKeyboardMarkup(List(
      List(nameButton),
      List(currencyButton),
      List(oneTimeButton),
      billingPeriodButton,
      List(firstPaymentDateButton),
      List(backButton)
    ))
  }
}
