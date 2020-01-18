package ru.johnspade.s10ns.bot.engine

import cats.Id
import cats.arrow.FunctionK
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.user.tags.{ChatId, FirstName, UserId}
import ru.johnspade.s10ns.user.{InMemoryUserRepository, User}
import cats.implicits._
import ru.johnspade.s10ns.bot.SettingsDialog
import ru.johnspade.s10ns.settings.SettingsDialogState
import telegramium.bots.{MarkupRemoveKeyboard, ReplyKeyboardRemove}

class DialogEngineSpec extends AnyFlatSpec with Matchers {
  private val userRepo = new InMemoryUserRepository
  private implicit val transact: FunctionK[Id, IO] = new FunctionK[Id, IO] {
    override def apply[A](fa: Id[A]): IO[A] = IO(fa)
  }
  private val dialogEngine = new DialogEngine[IO, Id](userRepo)

  private val user = User(UserId(1337L), FirstName("John"), ChatId(911L).some)
  private val dialog = SettingsDialog(SettingsDialogState.DefaultCurrency)

  "startDialog" should "update an user and remove the default keyboard" in {
    dialogEngine.startDialog(user, dialog, ReplyMessage("Hello")).unsafeRunSync shouldBe
      ReplyMessage("Hello", markup = MarkupRemoveKeyboard(ReplyKeyboardRemove(removeKeyboard = true)).some)
    userRepo.users.get(user.id) shouldBe user.copy(dialog = dialog.some).some
  }
}
