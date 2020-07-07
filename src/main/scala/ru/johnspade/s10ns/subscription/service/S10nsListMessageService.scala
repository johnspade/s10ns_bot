package ru.johnspade.s10ns.subscription.service

import cats.effect.Sync
import cats.implicits._
import org.joda.money.{CurrencyUnit, Money}
import ru.johnspade.s10ns.bot.Formatters.MoneyFormatter
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{EditS10n, EditS10nAmount, EditS10nBillingPeriod, EditS10nCurrency, EditS10nFirstPaymentDate, EditS10nName, EditS10nOneTime, MoneyService, Notify, RemoveS10n, S10n, S10ns, S10nsPeriod}
import ru.johnspade.s10ns.subscription.tags.{FirstPaymentDate, PageNumber}
import ru.johnspade.s10ns.subscription.{BillingPeriod, BillingPeriodUnit, Subscription}
import telegramium.bots.high._
import telegramium.bots.{Html, InlineKeyboardButton, Markdown}

class S10nsListMessageService[F[_] : Sync](
  private val moneyService: MoneyService[F],
  private val s10nInfoService: S10nInfoService[F]
) {
  private val DefaultPageSize = 10

  def createSubscriptionsPage(
    subscriptions: List[Subscription],
    page: PageNumber,
    defaultCurrency: CurrencyUnit,
    period: BillingPeriodUnit = BillingPeriodUnit.Month
  ): F[ReplyMessage] = {
    def createText(indexedSubscriptions: List[(Subscription, Int)], sum: Money) = {
      val sumString = period.measureUnit.getSubtype.capitalize + "ly: " + MoneyFormatter.print(sum) + "\n"
      indexedSubscriptions
        .traverse {
          case (s, i) =>
            s.billingPeriod.fold(s10nInfoService.printAmount(s.amount, defaultCurrency)) { billingPeriod =>
              val periodAmount = moneyService.calcAmount(billingPeriod, s.amount, period.chronoUnit)
              val periodAmountString = s10nInfoService.printAmount(periodAmount, defaultCurrency)
              val remainingTime = s.firstPaymentDate.flatTraverse {
                s10nInfoService.getRemainingTime(_, billingPeriod)
              }
                  .map(_.map(rem => s" <b>$rem</b>").getOrElse(""))
              for {
                periodAmountStr <- periodAmountString
                remaining <- remainingTime
              } yield periodAmountStr + remaining
            }
              .map(amount => s"$i. ${s.name} – $amount")
        }
        .map(s10ns => s"$sumString\n${s10ns.mkString("\n")}")
    }

    def createPeriodButton(): InlineKeyboardButton = {
      val nextPeriod = period match {
        case BillingPeriodUnit.Month => BillingPeriodUnit.Year
        case BillingPeriodUnit.Year => BillingPeriodUnit.Week
        case _ => BillingPeriodUnit.Month
      }
      inlineKeyboardButton(nextPeriod.measureUnit.getSubtype.capitalize + "ly", S10nsPeriod(nextPeriod, page))
    }

    def createSubscriptionButtons(indexedSubscriptions: List[(Subscription, Int)]): List[List[InlineKeyboardButton]] =
      indexedSubscriptions
        .grouped(5)
        .toList
        .map(_.map {
          case (s, i) => inlineKeyboardButton(i.toString, S10n(s.id, page))
        })

    def createNavButtons(listSize: Int) = {
      val pageLastElementNumber = DefaultPageSize * (page + 1)
      val leftButton = inlineKeyboardButton("⬅", S10ns(PageNumber(page - 1)))
      val rightButton = inlineKeyboardButton("➡", S10ns(PageNumber(page + 1)))
      List(
        (pageLastElementNumber > DefaultPageSize, leftButton),
        (pageLastElementNumber < listSize, rightButton)
      )
        .filter(_._1)
        .map(_._2)
    }

    def createReplyMessage(subscriptions: List[Subscription]): F[ReplyMessage] =
      moneyService.sum(subscriptions, defaultCurrency, period.chronoUnit)
        .flatMap { sum =>
          val from = page * DefaultPageSize
          val until = from + DefaultPageSize
          val indexedS10nsPage = subscriptions.slice(from, until).zipWithIndex.map {
            case (s, i) => (s, i + 1)
          }
          createText(indexedS10nsPage, sum).map { text =>
            val periodButtonRow = List(createPeriodButton())
            val subscriptionButtons = createSubscriptionButtons(indexedS10nsPage)
            val arrowButtons = createNavButtons(subscriptions.size)
            ReplyMessage(text, InlineKeyboardMarkup(periodButtonRow +: subscriptionButtons :+ arrowButtons).some, Html.some)
          }
        }

    createReplyMessage(subscriptions)
  }

  def createSubscriptionMessage(defaultCurrency: CurrencyUnit, s10n: Subscription, page: PageNumber = PageNumber(0)): F[ReplyMessage] = {
    val name = s10nInfoService.getName(s10n.name)
    val amount = s10nInfoService.getAmount(s10n.amount)
    val amountInDefaultCurrency = s10nInfoService.getAmountInDefaultCurrency(s10n.amount, defaultCurrency)
    val billingPeriod = s10n.billingPeriod.map(s10nInfoService.getBillingPeriod)

    def calcWithPeriod(f: (FirstPaymentDate, BillingPeriod) => F[String]): F[Option[String]] =
      s10n.firstPaymentDate.flatTraverse { start =>
        s10n.billingPeriod.traverse(f(start, _))
      }

    val nextPayment = calcWithPeriod { (start, billingPeriod) =>
      s10nInfoService.printNextPaymentDate(start, billingPeriod)
    }
    val firstPaymentDate = s10n.firstPaymentDate.map(s10nInfoService.getFirstPaymentDate)
    val paidInTotal = calcWithPeriod { (start, billingPeriod) =>
      s10nInfoService.getPaidInTotal(s10n.amount, start, billingPeriod)
    }

    def createMessage(amountDefault: Option[String], nextPayment: Option[String], total: Option[String]) = {
      val emptyLine = "".some
      val text = List(
        name.some,
        emptyLine,
        amount.some,
        amountDefault,
        emptyLine,
        billingPeriod,
        nextPayment,
        firstPaymentDate,
        total
      )
        .flatten
        .mkString("\n")
      val buttons = createS10nMessageMarkup(s10n, page)
      ReplyMessage(
        text,
        buttons.some,
        parseMode = Markdown.some
      )
    }

    for {
      amountDefault <- amountInDefaultCurrency
      total <- paidInTotal
      next <- nextPayment
    } yield createMessage(amountDefault, next, total)
  }

  def createS10nMessageMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup = {
    val editButton = inlineKeyboardButton("Edit", EditS10n(s10n.id, page))
    val notifyButton = inlineKeyboardButton(
      if (s10n.sendNotifications) "Disable notifications" else "Enable notifications",
      Notify(s10n.id, !s10n.sendNotifications, page)
    )
    val removeButton = inlineKeyboardButton("Remove", RemoveS10n(s10n.id, page))
    val backButton = inlineKeyboardButton("List", S10ns(page))
    InlineKeyboardMarkup.singleColumn(List(editButton, notifyButton, removeButton, backButton))
  }

  def createEditS10nMarkup(s10n: Subscription, page: PageNumber): InlineKeyboardMarkup = {
    import s10n.{id, oneTime}

    val nameButton = inlineKeyboardButton("Name", EditS10nName(id))
    val amountButton = inlineKeyboardButton("Amount", EditS10nAmount(id))
    val currencyButton = inlineKeyboardButton("Currency/amount", EditS10nCurrency(id))
    val oneTimeButton = inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id))
    val billingPeriodButton = if (oneTime.getOrElse(false)) List.empty
    else List(inlineKeyboardButton("Billing period", EditS10nBillingPeriod(id)))
    val firstPaymentDateButton = inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id))
    val backButton = inlineKeyboardButton("Back", S10n(id, page))
    InlineKeyboardMarkup(List(
      List(nameButton),
      List(amountButton),
      List(currencyButton),
      List(oneTimeButton),
      billingPeriodButton,
      List(firstPaymentDateButton),
      List(backButton)
    ))
  }
}
