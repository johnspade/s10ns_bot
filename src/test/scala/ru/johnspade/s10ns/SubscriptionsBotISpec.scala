package ru.johnspade.s10ns

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}

import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.option._
import cats.~>
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.flywaydb.core.Flyway
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.{BotStart, CbDataService, EditS10n, Markup, Messages, MoneyService, RemoveS10n, S10ns, StartController, StateMessageService}
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.settings.{DefaultSettingsService, SettingsController}
import ru.johnspade.s10ns.subscription.controller.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository
import ru.johnspade.s10ns.subscription.service.{DefaultCreateS10nDialogFsmService, DefaultCreateS10nDialogService, DefaultEditS10nDialogFsmService, DefaultEditS10nDialogService, DefaultSubscriptionListService, S10nInfoService, S10nsListMessageService}
import ru.johnspade.s10ns.subscription.tags.{PageNumber, SubscriptionId}
import ru.johnspade.s10ns.user.DoobieUserRepository
import telegramium.bots.client.{AnswerCallbackQueryReq, AnswerCallbackQueryRes, Api, EditMessageReplyMarkupReq, EditMessageReplyMarkupRes, SendMessageReq, SendMessageRes}
import telegramium.bots.{CallbackQuery, Chat, ChatIntId, InlineKeyboardMarkup, KeyboardMarkup, MarkupInlineKeyboard, Message, User}

import scala.concurrent.ExecutionContext

class SubscriptionsBotISpec
  extends AnyFreeSpec
    with BeforeAndAfterAll
    with ForAllTestContainer
    with MockFactory {
  override val container: PostgreSQLContainer = PostgreSQLContainer()

  import container.{container => pgContainer}

  override protected def beforeAll(): Unit = {
    Flyway
      .configure()
      .dataSource(pgContainer.getJdbcUrl, pgContainer.getUsername, pgContainer.getPassword)
      .load()
      .migrate
  }

  private val api = stub[Api[IO]]

  private val sendMessageResOk = IO(SendMessageRes(ok = true))

  "test /create" in new Wiring {
    prepareStubs()

    sendMessage("/start")
    verifySendMessage(BotStart.message.text, BotStart.markup.some).once

    sendMessage("/create")
    verifySendMessage(Messages.Currency, Markup.CurrencyReplyMarkup.some).once

    sendMessage("EUR")
    verifySendMessage(Messages.Name).once

    sendMessage("Netflix")
    verifySendMessage(Messages.Amount).once

    sendMessage("11.36")
    verifySendMessage(Messages.IsOneTime, Markup.isOneTimeReplyMarkup("Skip").some).once

    sendCallbackQuery("OneTime\u001Dfalse")
    verifySendMessage(Messages.BillingPeriodUnit, Markup.BillingPeriodUnitReplyMarkup.some).once

    sendCallbackQuery("PeriodUnit\u001DMonth")
    verifySendMessage(Messages.BillingPeriodDuration).once

    sendMessage("1")
    verifySendMessage(
      Messages.FirstPaymentDate,
      MarkupInlineKeyboard(calendarService.generateKeyboard(LocalDate.now(ZoneOffset.UTC))).some
    ).once

    sendCallbackQuery(s"FirstPayment\u001D$today")
    verifySendMessage(Messages.S10nSaved, BotStart.markup.some).once
    verifySendMessage(
      s"""*Netflix*
         |
         |11.36 EUR
         |
         |_Billing period:_ every 1 month
         |_Next payment:_ $today
         |_First payment:_ $today
         |_Paid in total:_ 0.00 EUR""".stripMargin,
      MarkupInlineKeyboard(InlineKeyboardMarkup(List(
        List(inlineKeyboardButton("Edit", EditS10n(SubscriptionId(1L), PageNumber(0)))),
        List(inlineKeyboardButton("Remove", RemoveS10n(SubscriptionId(1L), PageNumber(0)))),
        List(inlineKeyboardButton("List", S10ns(PageNumber(0))))
      ))).some,
      "Markdown".some
    ).once

    (api.editMessageReplyMarkup _).verify(EditMessageReplyMarkupReq(ChatIntId(0).some, 0.some)).repeat(3)
    (api.answerCallbackQuery _).verify(AnswerCallbackQueryReq("0")).repeat(3)
  }

  private val user = User(0, isBot = false, "John")

  private def createMessage(text: String) =
    Message(
      messageId = 0,
      date = 0,
      chat = Chat(id = 0, `type` = "private"),
      from = user.some,
      text = text.some
    )

  private def createCallbackQuery(data: String) =
    CallbackQuery(
      id = "0",
      from = user,
      chatInstance = "0",
      data = data.some,
      message = createMessage("").some
    )

  private def verifySendMessage(text: String, markup: Option[KeyboardMarkup] = None, parseMode: Option[String] = None) =
    (api.sendMessage _).verify(SendMessageReq(ChatIntId(0), text, replyMarkup = markup, parseMode = parseMode))

  private trait Wiring {
    private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    private implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger
    private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
    protected implicit val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      container.jdbcUrl,
      container.username,
      container.password
    )
    protected implicit val transact: ~>[ConnectionIO, IO] = new ~>[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(transactor)
    }

    protected val userRepo = new DoobieUserRepository
    private val s10nRepo = new DoobieSubscriptionRepository
    private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
    private val s10nsListMessageService = new S10nsListMessageService[IO](moneyService, new S10nInfoService[IO](moneyService))
    private val s10nsListService = new DefaultSubscriptionListService[IO, ConnectionIO](s10nRepo, s10nsListMessageService)
    private val s10nListController = new SubscriptionListController[IO](s10nsListService)
    protected val calendarService = new CalendarService
    private val stateMessageService = new StateMessageService[IO](calendarService)
    private val dialogEngine = new DefaultDialogEngine[IO, ConnectionIO](userRepo)
    private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, ConnectionIO](
      s10nRepo, userRepo, stateMessageService, dialogEngine, s10nsListMessageService
    )
    private val createS10nDialogService = new DefaultCreateS10nDialogService[IO, ConnectionIO](
      userRepo, createS10nDialogFsmService, stateMessageService, dialogEngine
    )
    private val createS10nDialogController = new CreateS10nDialogController[IO](createS10nDialogService)
    private val editS10nDialogFsmService = new DefaultEditS10nDialogFsmService[IO, ConnectionIO](
      s10nsListMessageService, stateMessageService, userRepo, s10nRepo, dialogEngine
    )
    private val editS10nDialogService = new DefaultEditS10nDialogService[IO, ConnectionIO](
      s10nRepo, editS10nDialogFsmService, stateMessageService, dialogEngine
    )
    private val editS10nDialogController = new EditS10nDialogController[IO](editS10nDialogService)
    private val calendarController = new CalendarController[IO](calendarService)
    private val settingsService = new DefaultSettingsService[IO](dialogEngine, stateMessageService)
    private val settingsController = new SettingsController[IO](settingsService)
    private val startController = new StartController[IO](dialogEngine)
    private val cbDataService = new CbDataService[IO]
    protected val bot = new SubscriptionsBot[IO, ConnectionIO](
      api,
      userRepo,
      s10nListController,
      createS10nDialogController,
      editS10nDialogController,
      calendarController,
      settingsController,
      startController,
      cbDataService
    )

    protected def sendMessage(text: String): Unit = bot.onMessage(createMessage(text)).unsafeRunSync

    protected def sendCallbackQuery(data: String): Unit = bot.onCallbackQuery(createCallbackQuery(data)).unsafeRunSync

    protected val today: String = DateTimeFormatter.ISO_DATE.format(LocalDate.now(ZoneOffset.UTC))

    protected def prepareStubs(): Unit = {
      (api.sendMessage _).when(*).returns(sendMessageResOk)
      (api.editMessageReplyMarkup _).when(*).returns(IO(EditMessageReplyMarkupRes(ok = true)))
      (api.answerCallbackQuery _).when(*).returns(IO(AnswerCallbackQueryRes(ok = true)))
    }
  }
}
