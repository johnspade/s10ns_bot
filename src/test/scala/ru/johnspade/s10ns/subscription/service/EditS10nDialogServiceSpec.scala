package ru.johnspade.s10ns.subscription.service

import scala.concurrent.ExecutionContext

import cats.Id
import cats.effect.Clock
import cats.effect.IO
import cats.syntax.option._

import org.scalamock.scalatest.MockFactory
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.BotStart
import ru.johnspade.s10ns.bot.EditS10n
import ru.johnspade.s10ns.bot.Messages
import ru.johnspade.s10ns.bot.MoneyService
import ru.johnspade.s10ns.bot.Notify
import ru.johnspade.s10ns.bot.RemoveS10n
import ru.johnspade.s10ns.bot.S10ns
import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.inlineKeyboardButton
import ru.johnspade.s10ns.exchangerates.InMemoryExchangeRatesStorage
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.repository.SubscriptionRepository
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.UserRepository

trait EditS10nDialogServiceSpec {
  this: MockFactory =>

  protected val mockS10nRepo: SubscriptionRepository[Id] = mock[SubscriptionRepository[Id]]
  protected val mockUserRepo: UserRepository[Id]         = mock[UserRepository[Id]]
  protected val dialogEngine                             = new DefaultDialogEngine[IO, Id](mockUserRepo)
  protected val moneyService                             = new MoneyService[IO](new InMemoryExchangeRatesStorage)
  protected val s10nsListMessageService = new S10nsListMessageService[IO](
    moneyService,
    new S10nInfoService[IO],
    new S10nsListReplyMessageService
  )

  protected val s10nId: Long             = 0L
  protected val user: User               = User(0L, "John", None)
  protected val draft: SubscriptionDraft = SubscriptionDraft.create(0L).copy(name = "Name")
  protected val s10n: Subscription       = Subscription.fromDraft(draft, s10nId)
  protected val page0: Int               = 0

  protected val defaultSavedMessage: ReplyMessage = ReplyMessage(Messages.S10nSaved, BotStart.markup.some)

  protected val defaultS10nMarkup: InlineKeyboardMarkup = InlineKeyboardMarkups.singleColumn(
    List(
      inlineKeyboardButton("Edit", EditS10n(s10nId, page0)),
      inlineKeyboardButton("Enable notifications", Notify(s10nId, enable = true, page0)),
      inlineKeyboardButton("Remove", RemoveS10n(s10nId, page0)),
      inlineKeyboardButton("List", S10ns(page0))
    )
  )
}
