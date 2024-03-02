package ru.johnspade.s10ns.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option._

import org.joda.money.CurrencyUnit
import org.joda.money.Money

import ru.johnspade.s10ns.SpecBase
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.service.S10nInfoService

class NotificationServiceSpec extends SpecBase {
  private val s10nInfoService     = new S10nInfoService[IO]
  private val hoursBefore         = 48
  private val notificationService = new NotificationService[IO](hoursBefore, s10nInfoService)

  private val sampleS10n = Subscription(
    id = 0L,
    userId = 0L,
    name = "Netflix",
    amount = Money.of(CurrencyUnit.EUR, 12),
    oneTime = false.some,
    billingPeriod = BillingPeriod(1, BillingPeriodUnit.Month).some,
    firstPaymentDate = LocalDate.now.plusDays(1).some,
    sendNotifications = true,
    lastNotification = None
  )
  private val now = Instant.now

  behavior of "needNotification"

  it should "return true if less than hoursBefore left" in {
    notificationService.needNotification(sampleS10n, now).unsafeRunSync() shouldBe true
  }

  it should "return false if more than hoursBefore left" in {
    notificationService
      .needNotification(
        sampleS10n.copy(firstPaymentDate = LocalDate.now.plusMonths(1).some),
        now
      )
      .unsafeRunSync() shouldBe false
  }

  it should "return false if sendNotifications is false" in {
    notificationService
      .needNotification(
        sampleS10n.copy(sendNotifications = false),
        now
      )
      .unsafeRunSync() shouldBe false
  }

  it should "return false if a notification is sent" in {
    notificationService
      .needNotification(
        sampleS10n.copy(lastNotification = now.some),
        now
      )
      .unsafeRunSync() shouldBe false
  }

  it should "return false if a notification was sent hoursBefore ago" in {
    notificationService.needNotification(
      sampleS10n.copy(
        lastNotification = now.minus(48, ChronoUnit.HOURS).some,
        firstPaymentDate = LocalDate.now.some
      ),
      now
    )
  }

  behavior of "isNotified"

  it should "return false if lastNotification is empty" in {
    notificationService.isNotified(sampleS10n, now) shouldBe false
  }

  it should "return true if lastNotification is less than x hours before now" in {
    notificationService.isNotified(sampleS10n.copy(lastNotification = Instant.now.some), now) shouldBe true
  }

  it should "return false if lastNotification is more than x hours before now" in {
    notificationService.isNotified(
      sampleS10n.copy(lastNotification = Instant.now.minus(30, ChronoUnit.DAYS).some),
      now
    ) shouldBe false
  }
}
