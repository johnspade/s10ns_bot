package ru.johnspade.s10ns.subscription

import java.time.LocalDate

import org.joda.money.Money

final case class S10nInfo(
    id: Long,
    name: String,
    amount: Money,
    amountInDefaultCurrency: S10nAmount,
    billingPeriod: Option[BillingPeriod],
    nextPaymentDate: Option[LocalDate],
    firstPaymentDate: Option[LocalDate],
    paidInTotal: Option[Money],
    oneTime: Option[Boolean],
    sendNotifications: Boolean,
    page: Int
)
