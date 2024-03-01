package ru.johnspade.s10ns.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option._

import doobie.ConnectionIO
import doobie.implicits._
import io.chrisdavenport.fuuid.FUUID
import org.joda.money.CurrencyUnit
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import telegramium.bots.Chat
import telegramium.bots.ChatIntId
import telegramium.bots.Markdown
import telegramium.bots.Message
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high._
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

import ru.johnspade.s10ns.PostgresContainer.transact
import ru.johnspade.s10ns.PostgresContainer.xa
import ru.johnspade.s10ns.SpecBase
import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository
import ru.johnspade.s10ns.subscription.service.S10nInfoService
import ru.johnspade.s10ns.subscription.service.S10nsListMessageService
import ru.johnspade.s10ns.subscription.service.S10nsListReplyMessageService
import ru.johnspade.s10ns.subscription.tags.BillingPeriodDuration
import ru.johnspade.s10ns.subscription.tags.FirstPaymentDate
import ru.johnspade.s10ns.subscription.tags.OneTimeSubscription
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.subscription.tags.SubscriptionAmount
import ru.johnspade.s10ns.subscription.tags.SubscriptionName
import ru.johnspade.s10ns.user.DoobieUserRepository
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags.ChatId
import ru.johnspade.s10ns.user.tags.FirstName
import ru.johnspade.s10ns.user.tags.UserId

class DefaultNotificationsJobServiceISpec extends SpecBase with MockFactory with BeforeAndAfterEach with OptionValues {

  private val notificationRepo = new DoobieNotificationRepository
  private val s10nRepo         = new DoobieSubscriptionRepository
  private val moneyService     = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nInfoService  = new S10nInfoService[IO]
  private val s10nsListMessageService =
    new S10nsListMessageService[IO](moneyService, s10nInfoService, new S10nsListReplyMessageService)
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
  private val userId        = UserId(911L)

  behavior of "executeTask"

  it should "send a notification if there is a task in the table" in {
    (api.execute[Message] _).when(*).returns(IO.pure(Message(0, date = 0, chat = Chat(0, `type` = ""))))
    notificationRepo
      .create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10nId))
      .transact(xa)
      .unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(
      sendMessage(
        chatId = ChatIntId(0),
        text = s"""_A payment date is approaching:_
           |*Netflix*
           |
           |11.36 €
           |
           |_Billing period:_ every 1 month
           |_Next payment:_ $today
           |_First payment:_ $today
           |_Paid in total:_ 0.00 €""".stripMargin,
        Markdown.some,
        replyMarkup = InlineKeyboardMarkups
          .singleColumn(
            List(
              inlineKeyboardButton("Edit", EditS10n(s10nId, PageNumber(0))),
              inlineKeyboardButton("Disable notifications", Notify(s10nId, enable = false, PageNumber(0))),
              inlineKeyboardButton("Remove", RemoveS10n(s10nId, PageNumber(0))),
              inlineKeyboardButton("List", S10ns(PageNumber(0)))
            )
          )
          .some
      )
    )
  }

  it should "do nothing if there is no tasks" in {
    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(*).never()
  }

  it should "do nothing if the notification already sent" in {
    val s10n = s10nRepo
      .create(
        SubscriptionDraft(
          userId = userId,
          name = SubscriptionName("Netflix"),
          currency = CurrencyUnit.EUR,
          amount = SubscriptionAmount(1136L),
          oneTime = OneTimeSubscription(false).some,
          periodDuration = BillingPeriodDuration(1).some,
          periodUnit = BillingPeriodUnit.Month.some,
          firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
          sendNotifications = true
        )
      )
      .transact(xa)
      .unsafeRunSync()
    s10nRepo.update(s10n.copy(lastNotification = Instant.now.some)).transact(xa).unsafeRunSync()
    notificationRepo
      .create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa)
      .unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()
    (api.execute[Message] _).verify(*).never()
  }

  it should "disable notifications if bot was blocked by the user" in {
    (api.execute[Message] _)
      .when(*)
      .returns(IO.raiseError(FailedRequest(Methods.getMe(), 403.some, "Forbidden: bot was blocked by the user".some)))
    val s10n = s10nRepo
      .create(
        SubscriptionDraft(
          userId = userId,
          name = SubscriptionName("Netflix"),
          currency = CurrencyUnit.EUR,
          amount = SubscriptionAmount(1136L),
          oneTime = OneTimeSubscription(false).some,
          periodDuration = BillingPeriodDuration(1).some,
          periodUnit = BillingPeriodUnit.Month.some,
          firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
          sendNotifications = true
        )
      )
      .transact(xa)
      .unsafeRunSync()
    notificationRepo
      .create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa)
      .unsafeRunSync()

    notificationsJobService.executeTask().unsafeRunSync()

    s10nRepo.getById(s10n.id).transact(xa).unsafeRunSync().value.sendNotifications shouldBe false
  }

  it should "not disable notifications on other errors" in {
    (api.execute[Message] _)
      .when(*)
      .returns(IO.raiseError(FailedRequest(Methods.getMe(), 500.some, "Failed".some)))
    val s10n = s10nRepo
      .create(
        SubscriptionDraft(
          userId = userId,
          name = SubscriptionName("Netflix"),
          currency = CurrencyUnit.EUR,
          amount = SubscriptionAmount(1136L),
          oneTime = OneTimeSubscription(false).some,
          periodDuration = BillingPeriodDuration(1).some,
          periodUnit = BillingPeriodUnit.Month.some,
          firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
          sendNotifications = true
        )
      )
      .transact(xa)
      .unsafeRunSync()
    notificationRepo
      .create(Notification(FUUID.randomFUUID[IO].unsafeRunSync(), s10n.id))
      .transact(xa)
      .unsafeRunSync()

    an[FailedRequest[Boolean]] shouldBe thrownBy(notificationsJobService.executeTask().unsafeRunSync())

    s10nRepo.getById(s10n.id).transact(xa).unsafeRunSync().value.sendNotifications shouldBe true
  }

  private val userRepo = new DoobieUserRepository

  private lazy val s10nId = s10nRepo.getByUserId(userId).transact(xa).unsafeRunSync().head.id

  override protected def beforeEach(): Unit = {
    userRepo.createOrUpdate(User(UserId(911L), FirstName("John"), ChatId(0L).some)).transact(xa).unsafeRunSync()
    s10nRepo
      .create(
        SubscriptionDraft(
          userId = userId,
          name = SubscriptionName("Netflix"),
          currency = CurrencyUnit.EUR,
          amount = SubscriptionAmount(1136L),
          oneTime = OneTimeSubscription(false).some,
          periodDuration = BillingPeriodDuration(1).some,
          periodUnit = BillingPeriodUnit.Month.some,
          firstPaymentDate = FirstPaymentDate(LocalDate.now(ZoneOffset.UTC)).some,
          sendNotifications = true
        )
      )
      .transact(xa)
      .unsafeRunSync()
  }

  override protected def afterEach(): Unit = {
    sql"delete from notifications where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from subscriptions where true".update.run.transact(xa).unsafeRunSync()
    sql"delete from users where true".update.run.transact(xa).unsafeRunSync()
  }
}
