package ru.johnspade.s10ns.subscription.service

import cats.Id
import cats.effect.{Clock, IO}
import cats.syntax.validated._
import cats.syntax.option._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, DefaultMsgService, ReplyMessage}
import ru.johnspade.s10ns.bot.{BotStart, EditS10n, EditS10nName, EditS10nNameDialog, Messages, MoneyService, NameTooLong, Notify, RemoveS10n, S10ns, TextCannotBeEmpty}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.dialog.EditS10nNameDialogState
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10nNameDialogService
import ru.johnspade.s10ns.subscription.tags.{PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import telegramium.bots.high.InlineKeyboardMarkup
import telegramium.bots.{Markdown, ReplyKeyboardRemove}

import scala.concurrent.ExecutionContext

class DefaultEditS10nNameDialogServiceSpec extends AnyFlatSpec with Matchers with MockFactory with DiffMatcher {
  private implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock

  private val mockS10nRepo = mock[SubscriptionRepository[Id]]
  private val userRepository = new InMemoryUserRepository
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepository)
  private val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  private val s10nsListMessageService = new S10nsListMessageService[IO](
    moneyService,
    new S10nInfoService[IO],
    new S10nsListReplyMessageService
  )

  private val editS10nNameDialogService = new DefaultEditS10nNameDialogService(
    s10nsListMessageService,
    new DefaultMsgService[IO, EditS10nNameDialogState],
    userRepository,
    mockS10nRepo,
    dialogEngine
  )

  private val s10nId = SubscriptionId(0L)
  private val user = User(UserId(0L), FirstName("John"), None)
  private val draft = SubscriptionDraft.create(UserId(0L))
  private val s10n = Subscription.fromDraft(draft, s10nId)
  private val page0 = PageNumber(0)

  "onEditS10nNameCb" should "ask for a new subscription's name" in {
    (mockS10nRepo.getById _).expects(*).returns(s10n.some)

    val result = editS10nNameDialogService.onEditS10nNameCb(user, EditS10nName(s10nId)).unsafeRunSync
    result should matchTo {
      List(ReplyMessage("Name:", ReplyKeyboardRemove(removeKeyboard = true).some))
    }
  }

  behavior of "saveName"

  it should "save a subscription with a new name" in {
    (mockS10nRepo.update _).expects(*).returns(s10n.copy(name = SubscriptionName("New name")).some)

    val dialog = EditS10nNameDialog(EditS10nNameDialogState.Name, s10n)
    editS10nNameDialogService.saveName(user, dialog, "New name".some).unsafeRunSync shouldBe
      List(
        ReplyMessage(Messages.S10nSaved, BotStart.markup.some),
        ReplyMessage(
          s"""|*New name*
              |
              |0.00 €
              |""".stripMargin,
          InlineKeyboardMarkup.singleColumn(List(
            inlineKeyboardButton("Edit", EditS10n(s10nId, page0)),
            inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, page0)),
            inlineKeyboardButton("Remove", RemoveS10n(s10nId, page0)),
            inlineKeyboardButton("List", S10ns(page0))
          )).some,
          Markdown.some

        )
      ).validNec
  }


  it should "fail if a text is missing" in {
    val dialog = EditS10nNameDialog(EditS10nNameDialogState.Name, s10n)
    editS10nNameDialogService.saveName(user, dialog, None).unsafeRunSync shouldBe TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a name is too long" in {
    val dialog = EditS10nNameDialog(EditS10nNameDialogState.Name, s10n)
    val name = List.fill(257)("a").mkString
    editS10nNameDialogService.saveName(user, dialog, name.some).unsafeRunSync shouldBe NameTooLong.invalidNec[String]
  }
}
