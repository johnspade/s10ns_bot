package ru.johnspade.s10ns

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}
import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.option._
import cats.~>
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import ru.johnspade.s10ns.PostgresContainer.container
import ru.johnspade.s10ns.TelegramiumScalamockUtils.verifyMethodCall
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, DefaultMsgService}
import ru.johnspade.s10ns.bot.{BotConfig, BotStart, CbDataService, EditS10n, IgnoreController, Markup, Messages, MoneyService, Notify, RemoveS10n, S10ns, StartController, UserMiddleware}
import ru.johnspade.s10ns.calendar.{CalendarController, CalendarService}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.settings.{DefaultSettingsService, SettingsController, SettingsDialogState}
import ru.johnspade.s10ns.subscription.controller.{S10nController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nMsgService, EditS10n1stPaymentDateMsgService, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nNameDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository
import ru.johnspade.s10ns.subscription.service.impl.{DefaultCreateS10nDialogFsmService, DefaultCreateS10nDialogService, DefaultEditS10n1stPaymentDateDialogService, DefaultEditS10nAmountDialogService, DefaultEditS10nBillingPeriodDialogService, DefaultEditS10nCurrencyDialogService, DefaultEditS10nNameDialogService, DefaultEditS10nOneTimeDialogService, DefaultSubscriptionListService}
import ru.johnspade.s10ns.subscription.service.{S10nInfoService, S10nsListMessageService, S10nsListReplyMessageService}
import ru.johnspade.s10ns.subscription.tags.PageNumber
import ru.johnspade.s10ns.user.DoobieUserRepository
import ru.johnspade.s10ns.user.tags.UserId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods._
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.{Api, _}
import telegramium.bots.{CallbackQuery, Chat, ChatIntId, Html, KeyboardMarkup, Markdown, Message, ParseMode, ReplyKeyboardRemove, User}
import tofu.logging.Logs

import scala.concurrent.ExecutionContext

class SubscriptionsBotISpec
  extends AnyFreeSpec
    with BeforeAndAfterAll
    with MockFactory {

  private implicit val api: Api[IO] = stub[Api[IO]]

  "test /create" in new Wiring {
    prepareStubs()

    sendMessage("/start")
    verifySendMessage(BotStart.message.text, BotStart.markup.some, parseMode = Html.some, disableWebPagePreview = true.some).once()

    sendMessage("/create")
    verifySendMessage(Messages.Currency, Markup.CurrencyReplyMarkup.some).once()

    sendMessage("EUR")
    verifySendMessage(Messages.Name, ReplyKeyboardRemove(removeKeyboard = true).some).once()

    sendMessage("Netflix")
    verifySendMessage(Messages.Amount).once()

    sendMessage("11.36")
    verifySendMessage(Messages.IsOneTime, Markup.isOneTimeReplyMarkup("Skip").some).once()

    sendCallbackQuery("OneTime\u001Dfalse")
    verifySendMessage(Messages.BillingPeriodUnit, Markup.BillingPeriodUnitReplyMarkup.some).once()

    sendCallbackQuery("PeriodUnit\u001DMonth")
    verifySendMessage(Messages.BillingPeriodDuration).once()
    verifyEditMessageText(" <em>Recurring</em>")

    sendMessage("1")
    verifySendMessage(
      Messages.FirstPaymentDate,
      calendarService.generateDaysKeyboard(LocalDate.now(ZoneOffset.UTC)).some
    ).once()
    verifyEditMessageText(" <em>Months</em>")

    sendCallbackQuery(s"FirstPayment\u001D$today")
    verifySendMessage(Messages.S10nSaved, BotStart.markup.some).once()
    verifySendMessage(
      s"""*Netflix*
         |
         |11.36 €
         |
         |_Billing period:_ every 1 month
         |_Next payment:_ $today
         |_First payment:_ $today
         |_Paid in total:_ 0.00 €""".stripMargin,
      InlineKeyboardMarkups.singleColumn(List(
        inlineKeyboardButton("Edit", EditS10n(s10nId, PageNumber(0))),
        inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, PageNumber(0))),
        inlineKeyboardButton("Remove", RemoveS10n(s10nId, PageNumber(0))),
        inlineKeyboardButton("List", S10ns(PageNumber(0)))
      )).some,
      Markdown.some
    ).once()
    verifyEditMessageText(s" <em>${DateTimeFormatter.ISO_DATE.format(LocalDate.now(ZoneOffset.UTC))}</em>")

    verifyMethodCall(api, editMessageReplyMarkup(ChatIntId(0).some, 0.some)).repeat(3)
    (api.execute[Boolean] _).verify(answerCallbackQuery("0")).repeat(3)
  }

  private val userId = 1337
  private val user = User(userId, isBot = false, "John")

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

  private def verifySendMessage(
    text: String,
    markup: Option[KeyboardMarkup] = None,
    parseMode: Option[ParseMode] = None,
    disableWebPagePreview: Option[Boolean] = None
  ) =
    (api.execute[Message] _).verify(sendMessage(
      ChatIntId(0),
      text,
      replyMarkup = markup,
      parseMode = parseMode,
      disableWebPagePreview = disableWebPagePreview
    ))

  private def verifyEditMessageText(text: String): Unit = {
    verifyMethodCall(api, editMessageText(ChatIntId(0).some, messageId = 0.some, text = text, parseMode = Html.some))
  }

  private val s10nRepo = new DoobieSubscriptionRepository
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  protected implicit val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    container.jdbcUrl,
    container.username,
    container.password
  )

  private lazy val s10nId = s10nRepo.getByUserId(UserId(userId.toLong)).transact(transactor).unsafeRunSync().head.id

  private trait Wiring {

    protected implicit val transact: ~>[ConnectionIO, IO] = new ~>[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(transactor)
    }

    private implicit val logs: Logs[IO, IO] = Logs.sync[IO, IO]
    protected val userRepo = new DoobieUserRepository

    private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
    private val s10nsListMessageService = new S10nsListMessageService[IO](
      moneyService,
      new S10nInfoService[IO],
      new S10nsListReplyMessageService
    )
    private val s10nsListService = new DefaultSubscriptionListService[IO, ConnectionIO](s10nRepo, s10nsListMessageService)
    private val s10nListController = new SubscriptionListController[IO](s10nsListService)
    protected val calendarService = new CalendarService
    private val dialogEngine = new DefaultDialogEngine[IO, ConnectionIO](userRepo)
    private val createS10nMsgService = new CreateS10nMsgService[IO](calendarService)
    private val createS10nDialogFsmService = new DefaultCreateS10nDialogFsmService[IO, ConnectionIO](
      s10nRepo, userRepo, dialogEngine, s10nsListMessageService, createS10nMsgService
    )
    private val createS10nDialogService = new DefaultCreateS10nDialogService[IO, ConnectionIO](
      createS10nDialogFsmService, createS10nMsgService, dialogEngine
    )
    val editS10n1stPaymentDateDialogService = new DefaultEditS10n1stPaymentDateDialogService[IO, ConnectionIO](
      s10nsListMessageService, new EditS10n1stPaymentDateMsgService[IO](calendarService), userRepo, s10nRepo, dialogEngine
    )
    val editS10nNameDialogService = new DefaultEditS10nNameDialogService[IO, ConnectionIO](
      s10nsListMessageService, new DefaultMsgService[IO, EditS10nNameDialogState], userRepo, s10nRepo, dialogEngine
    )
    val editS10nAmountDialogService = new DefaultEditS10nAmountDialogService[IO, ConnectionIO](
      s10nsListMessageService, new DefaultMsgService[IO, EditS10nAmountDialogState], userRepo, s10nRepo, dialogEngine
    )
    val editS10nBillingPeriodDialogService = new DefaultEditS10nBillingPeriodDialogService[IO, ConnectionIO](
      s10nsListMessageService, new DefaultMsgService[IO, EditS10nBillingPeriodDialogState], userRepo, s10nRepo, dialogEngine
    )
    val editS10nCurrencyDialogService = new DefaultEditS10nCurrencyDialogService[IO, ConnectionIO](
      s10nsListMessageService, new DefaultMsgService[IO, EditS10nCurrencyDialogState], userRepo, s10nRepo, dialogEngine
    )
    val editS10nOneTimeDialogService = new DefaultEditS10nOneTimeDialogService[IO, ConnectionIO](
      s10nsListMessageService, new DefaultMsgService[IO, EditS10nOneTimeDialogState], userRepo, s10nRepo, dialogEngine
    )
    private val s10nController = S10nController[IO](
      createS10nDialogService,
      editS10n1stPaymentDateDialogService,
      editS10nNameDialogService,
      editS10nAmountDialogService,
      editS10nBillingPeriodDialogService,
      editS10nCurrencyDialogService,
      editS10nOneTimeDialogService
    ).unsafeRunSync()
    private val calendarController = new CalendarController[IO](calendarService)
    private val settingsService = new DefaultSettingsService[IO](dialogEngine, new DefaultMsgService[IO, SettingsDialogState])
    private val settingsController = SettingsController[IO](settingsService).unsafeRunSync()
    private val startController = new StartController[IO](dialogEngine)
    private val cbDataService = new CbDataService[IO]
    private val botConfig = BotConfig("", 8080, "", "0.0.0.0")
    protected val bot: SubscriptionsBot[IO, ConnectionIO] = SubscriptionsBot[IO, ConnectionIO](
      botConfig,
      userRepo,
      s10nListController,
      s10nController,
      calendarController,
      settingsController,
      startController,
      new IgnoreController[IO],
      cbDataService,
      new UserMiddleware[IO, ConnectionIO](userRepo)
    ).unsafeRunSync()

    protected def sendMessage(text: String): Unit = bot.onMessage(createMessage(text)).unsafeRunSync()

    protected def sendCallbackQuery(data: String): Unit = bot.onCallbackQuery(createCallbackQuery(data)).unsafeRunSync()

    protected val today: String = DateTimeFormatter.ISO_DATE.format(LocalDate.now(ZoneOffset.UTC))

    private val mockMessage = Message(0, date = 0, chat = Chat(0, `type` = ""))

    protected def prepareStubs(): Unit = {
      (api.execute[Message] _)
        .when(where((_: Method[Message]).payload.name == "sendMessage"))
        .returns(IO.pure(mockMessage))
      (api.execute[Either[Boolean, Message]] _)
        .when(where((_: Method[Either[Boolean, Message]]).payload.name == "editMessageReplyMarkup"))
        .returns(IO.pure(Right(mockMessage)))
      (api.execute[Boolean] _)
        .when(where((_: Method[Boolean]).payload.name == "answerCallbackQuery"))
        .returns(IO.pure(true))
      (api.execute[Either[Boolean, Message]] _)
        .when(where((_: Method[Either[Boolean, Message]]).payload.name == "editMessageText"))
        .returns(IO.pure(Right(mockMessage)))
    }
  }
}
