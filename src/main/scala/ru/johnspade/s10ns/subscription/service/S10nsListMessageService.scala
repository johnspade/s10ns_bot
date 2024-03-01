package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate

import cats.Monad
import cats.effect.Sync
import cats.implicits._

import org.joda.money.CurrencyUnit
import org.joda.money.Money
import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.ExactAmount
import ru.johnspade.s10ns.subscription.NonExactAmount
import ru.johnspade.s10ns.subscription.S10nAmount
import ru.johnspade.s10ns.subscription.S10nInfo
import ru.johnspade.s10ns.subscription.S10nItem
import ru.johnspade.s10ns.subscription.S10nList
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.tags.FirstPaymentDate
import ru.johnspade.s10ns.subscription.tags.PageNumber

class S10nsListMessageService[F[_]: Monad](
  private val moneyService: MoneyService[F],
  private val s10nInfoService: S10nInfoService[F],
  private val s10nsListReplyMessageService: S10nsListReplyMessageService
) {
  private val DefaultPageSize = 10

  def createSubscriptionsPage(
    subscriptions: List[Subscription],
    page: PageNumber,
    defaultCurrency: CurrencyUnit,
    period: BillingPeriodUnit = BillingPeriodUnit.Month
  ): F[ReplyMessage] = {
    def withNextPaymentDate(s10n: Subscription) =
      s10n.firstPaymentDate
        .traverse(s10nInfoService.getNextPaymentDate(_, s10n.billingPeriod))
        .map((_, s10n))

    def createItem(nextPaymentDate: Option[LocalDate], s10n: Subscription, index: Int) = {
      val periodAmount = s10n.billingPeriod.fold(s10n.amount) {
        moneyService.calcAmount(_, s10n.amount, period.chronoUnit)
      }
      for {
        remainingTime <- nextPaymentDate.flatTraverse(s10nInfoService.getRemainingTime)
        amount <- getAmountInDefaultCurrency(periodAmount, defaultCurrency)
      } yield S10nItem(index + 1, s10n.id, s10n.name, amount, remainingTime)
    }

    def createItems() = {
      val from = page * DefaultPageSize
      val until = from + DefaultPageSize
      subscriptions
        .traverse(withNextPaymentDate)
        .flatMap {
          _.sortBy {
            case (nextPaymentDate, _) => nextPaymentDate
          }
            .slice(from, until)
            .mapWithIndex {
              case ((nextPaymentDate, s10n), i) => createItem(nextPaymentDate, s10n, i)
            }
            .sequence
        }
    }

    val nextPeriod = period match {
      case BillingPeriodUnit.Month => BillingPeriodUnit.Year
      case BillingPeriodUnit.Year => BillingPeriodUnit.Week
      case _ => BillingPeriodUnit.Month
    }

    moneyService.sum(subscriptions, defaultCurrency, period.chronoUnit)
      .flatMap { sum =>
        createItems()
          .map { items =>
            val list = S10nList(sum, period, items, nextPeriod, page, subscriptions.size)
            s10nsListReplyMessageService.createSubscriptionsPage(list, DefaultPageSize)
          }
      }
  }

  def createSubscriptionMessage(defaultCurrency: CurrencyUnit, s10n: Subscription, page: PageNumber = PageNumber(0)): F[ReplyMessage] = {
    val amountInDefaultCurrency = getAmountInDefaultCurrency(s10n.amount, defaultCurrency)

    def calcWithPeriod[T](f: (FirstPaymentDate, BillingPeriod) => F[T]): F[Option[T]] =
      s10n.firstPaymentDate.flatTraverse { start =>
        s10n.billingPeriod.traverse(f(start, _))
      }

    val nextPayment = calcWithPeriod { (start, billingPeriod) =>
      s10nInfoService.getNextPaymentDate(start, billingPeriod.some)
    }
    val paidInTotal = calcWithPeriod { (start, billingPeriod) =>
      s10nInfoService.getPaidInTotal(s10n.amount, start, billingPeriod)
    }

    for {
      amountDefault <- amountInDefaultCurrency
      total <- paidInTotal
      next <- nextPayment
    } yield s10nsListReplyMessageService.createSubscriptionMessage(S10nInfo(
      id = s10n.id,
      name = s10n.name,
      amount = s10n.amount,
      amountInDefaultCurrency = amountDefault,
      billingPeriod = s10n.billingPeriod,
      nextPaymentDate = next,
      firstPaymentDate = s10n.firstPaymentDate,
      paidInTotal = total,
      oneTime = s10n.oneTime,
      sendNotifications = s10n.sendNotifications,
      page = page
    ))
  }

  def createS10nMessageMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup =
    s10nsListReplyMessageService.createS10nMessageMarkup(s10n.id, s10n.sendNotifications, page)

  def createEditS10nMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup =
    s10nsListReplyMessageService.createEditS10nMarkup(s10n.id, s10n.oneTime, page)

  private def getAmountInDefaultCurrency(amount: Money, defaultCurrency: CurrencyUnit): F[S10nAmount] =
    if (amount.getCurrencyUnit == defaultCurrency) Monad[F].pure(ExactAmount(amount))
    else moneyService.convert(amount, defaultCurrency).map(_.map(NonExactAmount).getOrElse(ExactAmount(amount)))

}
