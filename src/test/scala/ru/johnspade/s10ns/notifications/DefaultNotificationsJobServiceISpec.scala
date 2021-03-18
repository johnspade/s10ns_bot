package ru.johnspade.s10ns.notifications

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}
import cats.effect.IO
import cats.syntax.option._
import doobie.ConnectionIO
import doobie.implicits._
import io.chrisdavenport.fuuid.FUUID
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import ru.johnspade.s10ns.PostgresContainer.{transact, xa}
import ru.johnspade.s10ns.SpecBase
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{EditS10n, MoneyService, Notify, RemoveS10n, S10ns}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{S10nInfoService, S10nsListMessageService, S10nsListReplyMessageService}
import ru.johnspade.s10ns.subscription.tags.{BillingPeriodDuration, FirstPaymentDate, OneTimeSubscription, PageNumber, SubscriptionAmount, SubscriptionName}
import ru.johnspade.s10ns.subscription.{BillingPeriodUnit, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{ChatId, FirstName, UserId}
import ru.johnspade.s10ns.user.{DoobieUserRepository, User}
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.{Api, _}
import telegramium.bots.{Chat, ChatIntId, Markdown, Message}

class DefaultNotificationsJobServiceISpec extends SpecBase with MockFactory with BeforeAndAfterEach with OptionValues {

  private val notificationRepo = new DoobieNotificationRepository
  private val s10nRepo = new DoobieSubscriptionRepository
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nInfoService = new S10nInfoService[IO]
  private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, s10nInfoService, new S10nsListReplyMessageService)
  private val notificationService = new NotificationService[IO](48, s10nInfoService)
  protected val notificationsJobService: DefaultNotificationsJobService[IO, ConnectionIO] =
    DefaultNotificationsJobService[IO, ConnectionIO](
      notificationRepo,
      s10nRepo,
      s10nsListMessageService,
      notificationService
    ).unsafeRunSync()

  private implicit val api: Api[IO] = stub[Api[IO]]

  private val today: String = DateTimeFormatter.ISO_DATE.format(LocalDate.now(ZoneOffset.UTC))
  private val userId = UserId(911L)

  behavior of "executeTask"

  it should "send a notification if there is a task in the table" in {
    (api.execute[Message] _).when(*).returns(IO.pure(Message(0, date = 0, chat = Chat(0, `type` = ""))))
    notificationRepo.create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10nId))
      .transact(xa).unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(sendMessage(
      chatId = ChatIntId(0),
      text =
        s"""_A payment date is approaching:_
           |*Netflix*
           |
           |11.36 €
           |
           |_Billing period:_ every 1 month
           |_Next payment:_ $today
           |_First payment:_ $today
           |_Paid in total:_ 0.00 €""".stripMargin,
      Markdown.some,
      replyMarkup = InlineKeyboardMarkups.singleColumn(List(
        inlineKeyboardButton("Edit", EditS10n(s10nId, PageNumber(0))),
        inlineKeyboardButton("Disable notifications", Notify(s10nId, enable = false, PageNumber(0))),
        inlineKeyboardButton("Remove", RemoveS10n(s10nId, PageNumber(0))),
        inlineKeyboardButton("List", S10ns(PageNumber(0)))
      )).some
    ))
  }

  it should "do nothing if there is no tasks" in {
    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(*).never()
  }

  it should "do nothing if the notification already sent" in {
    val s10n = s10nRepo.create(SubscriptionDraft(
      userId = userId,
      name = SubscriptionName("Netflix"),
      currency = CurrencyUnit.EUR,
      amount = SubscriptionAmount(1136L),
      oneTime = OneTimeSubscription(false).some,
      periodDuration = BillingPeriodDuration(1).some,
      periodUnit = BillingPeriodUnit.Month.some,
      firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
      sendNotifications = true
    ))
      .transact(xa)
      .unsafeRunSync()
    s10nRepo.update(s10n.copy(lastNotification = Instant.now.some)).transact(xa).unsafeRunSync()
    notificationRepo.create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa).unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(*).never()
  }

  it should "disable notifications if bot was blocked by the user" in {
    (api.execute[Message] _)
      .when(*)
      .returns(IO.raiseError(FailedRequest(Methods.sendChatAction(), 403.some, "Forbidden: bot was blocked by the user".some)))
    val s10n = s10nRepo.create(SubscriptionDraft(
      userId = userId,
      name = SubscriptionName("Netflix"),
      currency = CurrencyUnit.EUR,
      amount = SubscriptionAmount(1136L),
      oneTime = OneTimeSubscription(false).some,
      periodDuration = BillingPeriodDuration(1).some,
      periodUnit = BillingPeriodUnit.Month.some,
      firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
      sendNotifications = true
    ))
      .transact(xa)
      .unsafeRunSync()
    notificationRepo.create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa).unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()

    s10nRepo.getById(s10n.id).transact(xa).unsafeRunSync().value.sendNotifications shouldBe false
  }

  it should "not disable notifications on other errors" in {
    (api.execute[Message] _)
      .when(*)
      .returns(IO.raiseError(FailedRequest(Methods.sendChatAction(), 500.some, "Failed".some)))
    val s10n = s10nRepo.create(SubscriptionDraft(
      userId = userId,
      name = SubscriptionName("Netflix"),
      currency = CurrencyUnit.EUR,
      amount = SubscriptionAmount(1136L),
      oneTime = OneTimeSubscription(false).some,
      periodDuration = BillingPeriodDuration(1).some,
      periodUnit = BillingPeriodUnit.Month.some,
      firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
      sendNotifications = true
    ))
      .transact(xa)
      .unsafeRunSync()
    notificationRepo.create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa).unsafeRunSync()

    an[FailedRequest[Boolean]] shouldBe thrownBy(notificationsJobService.executeTask().unsafeRunSync())

    s10nRepo.getById(s10n.id).transact(xa).unsafeRunSync().value.sendNotifications shouldBe true
  }

  private val userRepo = new DoobieUserRepository

  private lazy val s10nId = s10nRepo.getByUserId(userId).transact(xa).unsafeRunSync().head.id

  override protected def beforeEach(): Unit = {
    userRepo.createOrUpdate(User(UserId(911L), FirstName("John"), ChatId(0L).some)).transact(xa).unsafeRunSync()
    s10nRepo.create(SubscriptionDraft(
      userId = userId,
      name = SubscriptionName("Netflix"),
      currency = CurrencyUnit.EUR,
      amount = SubscriptionAmount(1136L),
      oneTime = OneTimeSubscription(false).some,
      periodDuration = BillingPeriodDuration(1).some,
      periodUnit = BillingPeriodUnit.Month.some,
      firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
      sendNotifications = true
    ))
      .transact(xa)
      .unsafeRunSync()
  }

  override protected def afterEach(): Unit = {
    sql"delete from notifications where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from subscriptions where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from users where true".update.run.transact(xa).unsafeRunSync()
  }
}
