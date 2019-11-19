package ru.johnspade.s10ns.subscription

import java.time.format.DateTimeFormatter

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.joda.money.Money
import org.joda.money.format.MoneyFormatterBuilder
import ru.johnspade.s10ns.common.{Errors, PageNumber}
import ru.johnspade.s10ns.money.MoneyService
import ru.johnspade.s10ns.telegram.{CbData, ReplyMessage}
import ru.johnspade.s10ns.user.{User, UserId, UserRepository}
import telegramium.bots.{InlineKeyboardButton, InlineKeyboardMarkup, MarkupInlineKeyboard}

class S10nsListMessageService[F[_] : Sync](
  private val userRepo: UserRepository,
  private val subscriptionRepo: SubscriptionRepository,
  private val moneyService: MoneyService[F],
  private val xa: Transactor[F]
) {
  private val DefaultPageSize = 10
  private val MoneyFormatter =
    new MoneyFormatterBuilder()
      .appendAmount()
      .appendLiteral(" ")
      .appendCurrencySymbolLocalized()
      .toFormatter

  def createSubscriptionsPage(user: User, page: PageNumber): F[ReplyMessage] = {
    def createText(indexedSubscriptions: List[(Subscription, Int)], sum: Option[Money]) = {
      val sumString = sum.map(MoneyFormatter.print).getOrElse("")
      val list = indexedSubscriptions
        .map {
          case (s, i) => s"$i. ${s.name.value} – ${MoneyFormatter.print(s.amount)}"
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
      val pageLastElementNumber = DefaultPageSize * (page.value + 1)
      val leftButton = InlineKeyboardButton(
        "⬅",
        callbackData = CbData.subscriptions(PageNumber(page.value - 1)).some
      )
      val rightButton = InlineKeyboardButton(
        "➡",
        callbackData = CbData.subscriptions(PageNumber(page.value + 1)).some
      )
      List(
        (pageLastElementNumber > DefaultPageSize, leftButton),
        (pageLastElementNumber < indexedSubscriptions.size, rightButton)
      )
        .filter(_._1)
        .map(_._2)
    }

    def createReplyMessage(subscriptions: List[Subscription], user: User): F[ReplyMessage] =
      moneyService.sum(subscriptions, user).map { sum =>
        val from = page.value * DefaultPageSize
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

    subscriptionRepo.getByUserId(user.id)
      .transact(xa)
      .flatMap(subscriptions => createReplyMessage(subscriptions, user))
  }

  def createSubscriptionMessage(userId: UserId, subscriptionId: SubscriptionId, page: PageNumber): F[Either[String, ReplyMessage]] = {
    def createReplyMessage(subscription: Subscription): F[Option[ReplyMessage]] =
      userRepo.getById(userId).transact(xa).flatMap {
        _.traverse { user =>
          val name = subscription.name.value
          val amount = MoneyFormatter.print(subscription.amount)
          val amountInStandardCurrency = {
            val converted =
              if (subscription.amount.getCurrencyUnit == user.defaultCurrency) Monad[F].pure(Option.empty[Money])
              else moneyService.convert(subscription.amount, user.defaultCurrency)
            converted.map(_.map(MoneyFormatter.print))
          }
          val billingPeriod = subscription.billingPeriod.map { period =>
            val number = if (period.duration.value == 1) ""
            else s" ${period.duration.value}"
            val unitName = period.unit.value.toString.toLowerCase.reverse.replaceFirst("s", "").reverse
            s"Billing period: every$number $unitName"
          }
          val nextPayment = Option.empty[String] // todo
          val firstPaymentDate = subscription.firstPaymentDate.map { date =>
            s"First payment: ${DateTimeFormatter.ISO_DATE.format(date.value)}"
          }
          val paidInTotal = Option.empty[String] // todo
          amountInStandardCurrency.map { amountStd =>
            val text = Seq(name.some, amount.some, amountStd, billingPeriod, nextPayment, firstPaymentDate, paidInTotal)
              .flatten
              .mkString("\n")
            val editButton =
              InlineKeyboardButton("Edit", callbackData = CbData.editS10n(subscriptionId, page).some)
            val removeButton =
              InlineKeyboardButton("Remove", callbackData = CbData.removeSubscription(subscriptionId, page).some)
            val backButton = InlineKeyboardButton("List", callbackData = CbData.subscriptions(page).some)
            val buttonsList = List(List(editButton), List(removeButton), List(backButton))
            ReplyMessage(text, MarkupInlineKeyboard(InlineKeyboardMarkup(buttonsList)).some)
          }
        }
      }

    def checkUserAndGetMessage(subscription: Subscription) =
      Either.cond(subscription.userId == userId, createReplyMessage(subscription), Errors.accessDenied)
        .sequence
        .map(_.sequence)

    subscriptionRepo.getById(subscriptionId)
      .transact(xa)
      .flatMap {
        _.flatTraverse(checkUserAndGetMessage)
          .map(_.toRight(Errors.notFound).flatten)
      }
  }

  def createEditS10nMarkup(userId: UserId, subscriptionId: SubscriptionId, page: PageNumber): F[Either[String, InlineKeyboardMarkup]] = {
    def createMarkup(subscription: Subscription) = {
      val nameButton = InlineKeyboardButton("Edit name", callbackData = CbData.editS10nName(subscriptionId).some)
      val backButton = InlineKeyboardButton("Back", callbackData = CbData.subscription(subscriptionId, page).some)
      InlineKeyboardMarkup(List(List(nameButton), List(backButton)))
    }

    def checkUserAndGetMarkup(subscription: Subscription) =
      Either.cond(subscription.userId == userId, createMarkup(subscription), Errors.accessDenied)

    subscriptionRepo.getById(subscriptionId)
      .transact(xa)
      .map {
        _.map(checkUserAndGetMarkup)
          .toRight(Errors.notFound).flatten
      }
  }
}
