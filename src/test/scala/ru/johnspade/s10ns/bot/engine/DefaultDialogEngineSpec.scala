package ru.johnspade.s10ns.bot.engine

import cats.Id
import cats.effect.IO
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.{BotStart, SettingsDialog}
import ru.johnspade.s10ns.settings.SettingsDialogState
import ru.johnspade.s10ns.user.tags.{ChatId, FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import telegramium.bots.{MarkupRemoveKeyboard, ReplyKeyboardRemove}

class DefaultDialogEngineSpec extends AnyFlatSpec with Matchers {
  private val userRepo = new InMemoryUserRepository
  private val dialogEngine = new DefaultDialogEngine[IO, Id](userRepo)

  private val user = User(UserId(1337L), FirstName("John"), ChatId(911L).some)
  private val dialog = SettingsDialog(SettingsDialogState.DefaultCurrency)
  private val userWithDialog = user.copy(dialog = dialog.some)

  "startDialog" should "update an user and remove the default keyboard" in {
    dialogEngine.startDialog(user, dialog, ReplyMessage("Hello")).unsafeRunSync shouldBe
      ReplyMessage("Hello", markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some)
    userRepo.users.get(user.id) shouldBe user.copy(dialog = dialog.some).some
  }

  "reset" should "clear dialogs and enable the default keyboard" in {
    dialogEngine.reset(userWithDialog, "Reset") shouldBe ReplyMessage("Reset", BotStart.markup.some)
    userRepo.users.get(user.id) shouldBe user.some
  }

  "resetAndCommit" should "clear dialogs and enable the default keyboard" in {
    dialogEngine.resetAndCommit(userWithDialog, "Reset").unsafeRunSync shouldBe ReplyMessage("Reset", BotStart.markup.some)
    userRepo.users.get(user.id) shouldBe user.some
  }

  "sayHi" should "clear dialogs and welcome an user" in {
    dialogEngine.sayHi(userWithDialog).unsafeRunSync shouldBe
      ReplyMessage(
        "Manage your subscriptions and get detailed insights of your recurring expenses.",
        markup = BotStart.markup.some
      )
    userRepo.users.get(user.id) shouldBe user.some
  }
}
