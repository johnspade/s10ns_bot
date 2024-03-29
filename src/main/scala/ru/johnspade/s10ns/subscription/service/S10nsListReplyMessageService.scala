package ru.johnspade.s10ns.subscription.service

import java.time.format.DateTimeFormatter

import cats.syntax.option._

import com.ibm.icu.text.MeasureFormat
import com.ibm.icu.text.MeasureFormat.FormatWidth
import com.ibm.icu.util.Measure
import com.ibm.icu.util.ULocale
import telegramium.bots.Html
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.Markdown
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.EditS10nAmount
import ru.johnspade.s10ns.bot.EditS10nBillingPeriod
import ru.johnspade.s10ns.bot.EditS10nCurrency
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDate
import ru.johnspade.s10ns.bot.EditS10nName
import ru.johnspade.s10ns.bot.EditS10nOneTime
import ru.johnspade.s10ns.bot.Formatters.MoneyFormatter
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.S10nsPeriod
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.subscription.ExactAmount
import ru.johnspade.s10ns.subscription.NonExactAmount
import ru.johnspade.s10ns.subscription.S10nInfo
import ru.johnspade.s10ns.subscription.S10nList

// todo write unit tests
class S10nsListReplyMessageService {
  def createSubscriptionsPage(list: S10nList, defaultPageSize: Int): ReplyMessage = {
    import list.{items, nextPeriod, page, period, sum, totalSize}

    def createText() = {
      val total = period.measureUnit.getSubtype.capitalize + "ly: " + MoneyFormatter.print(sum) + "\n"
      val s10ns = items.map { item =>
        import item.{amount, index, name, remainingTime}

        val remaining = remainingTime.fold("")(rem => s" <b>[${rem.count}${rem.unit.toString.head.toLower}]</b>")
        val amountPrefix = amount match {
          case ExactAmount(_)    => ""
          case NonExactAmount(_) => "≈"
        }
        val formattedAmount = amountPrefix + MoneyFormatter.print(amount.amount)
        s"$index. $name – $formattedAmount$remaining"
      }
      s"$total\n${s10ns.mkString("\n")}"
    }

    def createPeriodButton(): InlineKeyboardButton =
      inlineKeyboardButton(nextPeriod.measureUnit.getSubtype.capitalize + "ly", S10nsPeriod(nextPeriod, page))

    def createSubscriptionButtons(): List[List[InlineKeyboardButton]] =
      list.items
        .grouped(5)
        .toList
        .map(_.map { item =>
          inlineKeyboardButton(item.index.toString, S10n(item.id, list.page))
        })

    def createNavButtons(listSize: Int) = {
      val pageLastElementNumber = defaultPageSize * (page + 1)
      val leftButton            = inlineKeyboardButton("⬅", S10ns(page - 1))
      val rightButton           = inlineKeyboardButton("➡", S10ns(page + 1))
      List(
        (pageLastElementNumber > defaultPageSize, leftButton),
        (pageLastElementNumber < listSize, rightButton)
      )
        .filter(_._1)
        .map(_._2)
    }

    val periodAndDonateRow =
      List(createPeriodButton(), InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade"))
    val subscriptionButtons = createSubscriptionButtons()
    val arrowButtons        = createNavButtons(totalSize)
    ReplyMessage(
      createText(),
      InlineKeyboardMarkup(subscriptionButtons ++ List(arrowButtons, periodAndDonateRow)).some,
      Html.some
    )
  }

  def createSubscriptionMessage(s10nInfo: S10nInfo): ReplyMessage = {
    import s10nInfo.{
      amount,
      amountInDefaultCurrency,
      billingPeriod,
      firstPaymentDate,
      id,
      name,
      nextPaymentDate,
      page,
      paidInTotal,
      sendNotifications
    }

    val billingPeriodStr = billingPeriod.map { period =>
      val measure = measureFormat.format(new Measure(period.duration, period.unit.measureUnit))
      s"_Billing period:_ every $measure"
    }
    val nextPaymentDateStr = nextPaymentDate.map { date =>
      s"_Next payment:_ ${DateTimeFormatter.ISO_DATE.format(date)}"
    }
    val firstPaymentDateStr = firstPaymentDate.map { start =>
      s"_First payment:_ ${DateTimeFormatter.ISO_DATE.format(start)}"
    }
    val paidInTotalStr = paidInTotal.map { total =>
      s"_Paid in total:_ ${MoneyFormatter.print(total)}"
    }
    val amountInDefaultCurrencyStr = amountInDefaultCurrency match {
      case ExactAmount(_)         => None
      case NonExactAmount(amount) => s"≈${MoneyFormatter.print(amount)}".some
    }
    val emptyLine = "".some
    val text = List(
      s"*$name*".some,
      emptyLine,
      MoneyFormatter.print(amount).some,
      amountInDefaultCurrencyStr,
      emptyLine,
      billingPeriodStr,
      nextPaymentDateStr,
      firstPaymentDateStr,
      paidInTotalStr
    ).flatten
      .mkString("\n")
    val buttons = createS10nMessageMarkup(id, sendNotifications = sendNotifications, page)
    ReplyMessage(
      text,
      buttons.some,
      parseMode = Markdown.some
    )
  }

  def createS10nMessageMarkup(
      id: Long,
      sendNotifications: Boolean,
      page: Int
  ): InlineKeyboardMarkup = {
    val editButton = inlineKeyboardButton("Edit", EditS10n(id, page))
    val notifyButton = inlineKeyboardButton(
      if (sendNotifications) "Disable notifications" else "Enable notifications",
      Notify(id, !sendNotifications, page)
    )
    val removeButton = inlineKeyboardButton("Remove", RemoveS10n(id, page))
    val backButton   = inlineKeyboardButton("List", S10ns(page))
    InlineKeyboardMarkups.singleColumn(List(editButton, notifyButton, removeButton, backButton))
  }

  def createEditS10nMarkup(
      id: Long,
      oneTime: Option[Boolean],
      page: Int
  ): InlineKeyboardMarkup = {
    val nameButton     = inlineKeyboardButton("Name", EditS10nName(id))
    val amountButton   = inlineKeyboardButton("Amount", EditS10nAmount(id))
    val currencyButton = inlineKeyboardButton("Currency/amount", EditS10nCurrency(id))
    val oneTimeButton  = inlineKeyboardButton("Recurring/one time", EditS10nOneTime(id))
    val billingPeriodButton =
      if (oneTime.getOrElse(false)) List.empty
      else List(inlineKeyboardButton("Billing period", EditS10nBillingPeriod(id)))
    val firstPaymentDateButton = inlineKeyboardButton("First payment date", EditS10nFirstPaymentDate(id))
    val backButton             = inlineKeyboardButton("Back", S10n(id, page))
    InlineKeyboardMarkup(
      List(
        List(nameButton),
        List(amountButton),
        List(currencyButton),
        List(oneTimeButton),
        billingPeriodButton,
        List(firstPaymentDateButton),
        List(backButton)
      )
    )
  }

  private val measureFormat = MeasureFormat.getInstance(ULocale.US, FormatWidth.WIDE)
}
