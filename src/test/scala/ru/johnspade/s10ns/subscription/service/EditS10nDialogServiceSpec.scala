package ru.johnspade.s10ns.subscription.service

import cats.Id
import cats.effect.{Clock, IO}
import cats.syntax.option._
import org.scalamock.scalatest.MockFactory
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, ReplyMessage}
import ru.johnspade.s10ns.bot.{BotStart, EditS10n, Messages, MoneyService, Notify, RemoveS10n, S10ns}
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.subscription.tags.{PageNumber, SubscriptionId, SubscriptionName}
import ru.johnspade.s10ns.subscription.{Subscription, SubscriptionDraft}
import ru.johnspade.s10ns.user.tags.{FirstName, UserId}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.InlineKeyboardMarkup

import scala.concurrent.ExecutionContext

trait EditS10nDialogServiceSpec extends MockFactory {
  protected implicit val clock: Clock[IO] = IO.timer(ExecutionContext.global).clock

  protected val mockS10nRepo: SubscriptionRepository[Id] = mock[SubscriptionRepository[Id]]
  protected val mockUserRepo: UserRepository[Id] = mock[UserRepository[Id]]
  protected val dialogEngine = new DefaultDialogEngine[IO, Id](mockUserRepo)
  protected val moneyService = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  protected val s10nsListMessageService = new S10nsListMessageService[IO](
    moneyService,
    new S10nInfoService[IO],
    new S10nsListReplyMessageService
  )

  protected val s10nId: SubscriptionId = SubscriptionId(0L)
  protected val user: User = User(UserId(0L), FirstName("John"), None)
  protected val draft: SubscriptionDraft = SubscriptionDraft.create(UserId(0L)).copy(name = SubscriptionName("Name"))
  protected val s10n: Subscription = Subscription.fromDraft(draft, s10nId)
  protected val page0: PageNumber = PageNumber(0)

  protected val defaultSavedMessage: ReplyMessage = ReplyMessage(Messages.S10nSaved, BotStart.markup.some)

  protected val defaultS10nMarkup: InlineKeyboardMarkup = InlineKeyboardMarkups.singleColumn(List(
    inlineKeyboardButton("Edit", EditS10n(s10nId, page0)),
    inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, page0)),
    inlineKeyboardButton("Remove", RemoveS10n(s10nId, page0)),
    inlineKeyboardButton("List", S10ns(page0))
  ))
}
