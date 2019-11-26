package ru.johnspade.s10ns.subscription

import java.time.format.DateTimeFormatter

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import org.joda.money.format.MoneyFormatterBuilder
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.common.tags._
import ru.johnspade.s10ns.money.MoneyService
import ru.johnspade.s10ns.telegram.{CbData, ReplyMessage}
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
          case (s, i) =>
            InlineKeyboardButton(
              i.toString,
              callbackData = CbData.subscription(s.id, page).some
            )
        })

    def createNavButtons(indexedSubscriptions: List[(Subscription, Int)]): List[InlineKeyboardButton] = {
      val pageLastElementNumber = DefaultPageSize * (page + 1)
      val leftButton = InlineKeyboardButton(
        "⬅",
        callbackData = CbData.subscriptions(PageNumber(page - 1)).some
      )
      val rightButton = InlineKeyboardButton(
        "➡",
        callbackData = CbData.subscriptions(PageNumber(page + 1)).some
      )
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

  def createSubscriptionMessage(user: User, subscription: Subscription, page: PageNumber): F[ReplyMessage] = {
    val name = subscription.name
    val amount = MoneyFormatter.print(subscription.amount)
    val amountInStandardCurrency = {
      val converted =
        if (subscription.amount.getCurrencyUnit == user.defaultCurrency) Monad[F].pure(Option.empty[Money])
        else moneyService.convert(subscription.amount, user.defaultCurrency)
      converted.map(_.map(MoneyFormatter.print))
    }
    val billingPeriod = subscription.billingPeriod.map { period =>
      val number = if (period.duration == 1) ""
      else s" ${period.duration}"
      val unitName = period.unit.toString.toLowerCase.reverse.replaceFirst("s", "").reverse
      s"Billing period: every$number $unitName"
    }
    val nextPayment = Option.empty[String] // todo
    val firstPaymentDate = subscription.firstPaymentDate.map { date =>
      s"First payment: ${DateTimeFormatter.ISO_DATE.format(date)}"
    }
    val paidInTotal = Option.empty[String] // todo
    amountInStandardCurrency.map { amountStd =>
      val text = Seq(name.some, amount.some, amountStd, billingPeriod, nextPayment, firstPaymentDate, paidInTotal)
        .flatten
        .mkString("\n")
      val editButton =
        InlineKeyboardButton("Edit", callbackData = CbData.editS10n(subscription.id, page).some)
      val removeButton =
        InlineKeyboardButton("Remove", callbackData = CbData.removeSubscription(subscription.id, page).some)
      val backButton = InlineKeyboardButton("List", callbackData = CbData.subscriptions(page).some)
      val buttonsList = List(List(editButton), List(removeButton), List(backButton))
      ReplyMessage(text, MarkupInlineKeyboard(InlineKeyboardMarkup(buttonsList)).some)
    }
  }

  def createEditS10nMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup = {
    val nameButton = InlineKeyboardButton("Edit name", callbackData = CbData.editS10nName(s10n.id).some)
    val backButton = InlineKeyboardButton("Back", callbackData = CbData.subscription(s10n.id, page).some)
    InlineKeyboardMarkup(List(List(nameButton), List(backButton)))
  }
}
