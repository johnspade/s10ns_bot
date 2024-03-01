package ru.johnspade.s10ns.bot.engine

import cats.Id
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.Html
import telegramium.bots.ReplyKeyboardRemove

import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.BotStart
import ru.johnspade.s10ns.bot.SettingsDialog
import ru.johnspade.s10ns.settings.SettingsDialogState
import ru.johnspade.s10ns.user.InMemoryUserRepository
import ru.johnspade.s10ns.user.User
import ru.johnspade.s10ns.user.tags.ChatId
import ru.johnspade.s10ns.user.tags.FirstName
import ru.johnspade.s10ns.user.tags.UserId

class DefaultDialogEngineSpec extends AnyFlatSpec with Matchers {
  private val userRepo = new InMemoryUserRepository
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepo)

  private val user = User(UserId(1337L), FirstName("John"), ChatId(911L).some)
  private val dialog = SettingsDialog(SettingsDialogState.DefaultCurrency)
  private val userWithDialog = user.copy(dialog = dialog.some)

  "startDialog" should "update an user and remove the default keyboard" in {
    val message = ReplyMessage("Hello")
    dialogEngine.startDialog(user, dialog, message).unsafeRunSync() shouldBe
      List(ReplyMessage("Hello", markup = ReplyKeyboardRemove(removeKeyboard = true).some))
    userRepo.users.get(user.id) shouldBe user.copy(dialog = dialog.some).some
  }

  "reset" should "clear dialogs and enable the default keyboard" in {
    dialogEngine.reset(userWithDialog, "Reset") shouldBe ReplyMessage("Reset", BotStart.markup.some)
    userRepo.users.get(user.id) shouldBe user.some
  }

  "resetAndCommit" should "clear dialogs and enable the default keyboard" in {
    dialogEngine.resetAndCommit(userWithDialog, "Reset").unsafeRunSync() shouldBe ReplyMessage("Reset", BotStart.markup.some)
    userRepo.users.get(user.id) shouldBe user.some
  }

  "sayHi" should "clear dialogs and welcome an user" in {
    dialogEngine.sayHi(userWithDialog).unsafeRunSync() shouldBe
      ReplyMessage(
        """Manage your subscriptions and get detailed insights of your recurring expenses.
          |
          |Select your default currency: /settings. Enter a currency code manually if it's not on the list.
          |
          |<a href="https://buymeacoff.ee/johnspade">Buy me a coffee</a> ☕""".stripMargin,
        markup = BotStart.markup.some,
        parseMode = Html.some,
        disableWebPagePreview = true.some
      )
    userRepo.users.get(user.id) shouldBe user.some
  }
}
