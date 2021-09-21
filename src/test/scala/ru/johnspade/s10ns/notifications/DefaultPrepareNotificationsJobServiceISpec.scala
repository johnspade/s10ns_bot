package ru.johnspade.s10ns.notifications

import java.time.{Instant, LocalDate}

import cats.effect.IO
import cats.syntax.option._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import org.joda.money.CurrencyUnit
import org.scalatest.BeforeAndAfterEach
import ru.johnspade.s10ns.PostgresContainer.{transact, xa}
import ru.johnspade.s10ns.SpecBase
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nInfoService
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, OneTimeSubscription, SubscriptionAmount, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{DoobieUserRepository, User}
import cats.effect.unsafe.implicits.global

class DefaultPrepareNotificationsJobServiceISpec extends SpecBase with BeforeAndAfterEach {

  behavior of "prepareNotifications"

  it should "create notifications if less than hoursBefore left" in new Wiring {
    val s10n1: Subscription = createS10n(sampleS10nDraft)
    val s10n2: Subscription = createS10n(sampleS10nDraft)
    createS10n(sampleS10nDraft.copy(firstPaymentDate = FirstPaymentDate(LocalDate.now.plusMonths(1)).some))

    val notifications: List[Notification] = prepareAndGetNotifications
    notifications.head.subscriptionId shouldBe s10n1.id
    notifications.last.subscriptionId shouldBe s10n2.id
  }

  it should "not create notifications if they're already exist" in new Wiring {
    val s10n1: Subscription = createS10n(sampleS10nDraft)
    prepareAndGetNotifications
    val notifications: List[Notification] = prepareAndGetNotifications
    notifications.head.subscriptionId shouldBe s10n1.id
    notifications.size shouldBe 1
  }

  it should "not create notifications if they're already sent" in new Wiring {
    val s10n1: Subscription = createS10n(sampleS10nDraft)
    s10nRepo.update(s10n1.copy(lastNotification = Instant.now.some)).transact(xa).unsafeRunSync()

    prepareAndGetNotifications shouldBe empty
  }

  it should "not create notifications for subscriptions without payment dates" in new Wiring {
    createS10n(sampleS10nDraft.copy(firstPaymentDate = None))
    prepareAndGetNotifications shouldBe empty
  }

  it should "create notifications for subscriptions without billing periods" in new Wiring {
    val s10n1: Subscription = createS10n(sampleS10nDraft.copy(periodDuration = None, periodUnit = None))
    prepareAndGetNotifications.head.subscriptionId shouldBe s10n1.id
  }

  private trait Wiring {
    protected val s10nRepo = new DoobieSubscriptionRepository
    protected val notificationRepo = new DoobieNotificationRepository
    private val s10nInfoService = new S10nInfoService[IO]
    private val hoursBefore = 48
    private val notificationService = new NotificationService[IO](hoursBefore, s10nInfoService)

    protected val prepareNotificationsJobService: DefaultPrepareNotificationsJobService[IO, ConnectionIO] =
      DefaultPrepareNotificationsJobService(s10nRepo, notificationRepo, notificationService).unsafeRunSync()

    protected val sampleS10nDraft: SubscriptionDraft =
      SubscriptionDraft(
        userId = UserId(0L),
        name = SubscriptionName("Netflix"),
        currency = CurrencyUnit.EUR,
        amount = SubscriptionAmount(100L),
        oneTime = OneTimeSubscription(false).some,
        periodDuration = BillingPeriodDuration(1).some,
        periodUnit = BillingPeriodUnit.Month.some,
        firstPaymentDate = FirstPaymentDate(LocalDate.now.plusDays(1)).some,
        sendNotifications = true
      )

    protected def createS10n(draft: SubscriptionDraft): Subscription = s10nRepo.create(draft).transact(xa).unsafeRunSync()

    protected def prepareAndGetNotifications: List[Notification] = {
      prepareNotificationsJobService.prepareNotifications().unsafeRunSync()
      notificationRepo.getAll.transact(xa).unsafeRunSync()
    }
  }

  private val userRepo = new DoobieUserRepository

  override protected def beforeEach(): Unit = {
    userRepo.createOrUpdate(User(id = UserId(0L), FirstName("John"), None)).transact(xa).unsafeRunSync()
  }

  override protected def afterEach(): Unit = {
    sql"delete from notifications where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from subscriptions where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from users where true".update.run.transact(xa).unsafeRunSync()
  }
}
