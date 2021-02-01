package ru.johnspade.s10ns.subscription.service

import cats.effect.IO
import cats.syntax.option._
import cats.syntax.validated._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.{DefaultMsgService, ReplyMessage}
import ru.johnspade.s10ns.bot.{EditS10nName, EditS10nNameDialog, NameTooLong, TextCannotBeEmpty}
import ru.johnspade.s10ns.subscription.dialog.EditS10nNameDialogState
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10nNameDialogService
import ru.johnspade.s10ns.subscription.tags.SubscriptionName
import telegramium.bots.{Markdown, ReplyKeyboardRemove}

class DefaultEditS10nNameDialogServiceSpec extends AnyFlatSpec with Matchers with DiffMatcher with EditS10nDialogServiceSpec {
  private val editS10nNameDialogService = new DefaultEditS10nNameDialogService(
    s10nsListMessageService,
    new DefaultMsgService[IO, EditS10nNameDialogState],
    mockUserRepo,
    mockS10nRepo,
    dialogEngine
  )
  private val dialog = EditS10nNameDialog(EditS10nNameDialogState.Name, s10n)

  "onEditS10nNameCb" should "ask for a new subscription's name" in {
    (mockS10nRepo.getById _).expects(s10nId).returns(s10n.some)
    val updatedUser = user.copy(dialog = dialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val result = editS10nNameDialogService.onEditS10nNameCb(user, EditS10nName(s10nId)).unsafeRunSync()
    result should matchTo {
      List(ReplyMessage("Name:", ReplyKeyboardRemove(removeKeyboard = true).some))
    }
  }

  behavior of "saveName"

  it should "save a subscription with a new name" in {
    val updatedS10n = s10n.copy(name = SubscriptionName("New name"))
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    editS10nNameDialogService.saveName(user, dialog, "New name".some).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*New name*
              |
              |0.00 €
              |""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      ).validNec
  }


  it should "fail if a text is missing" in {
    editS10nNameDialogService.saveName(user, dialog, None).unsafeRunSync() shouldBe TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a name is too long" in {
    val name = List.fill(257)("a").mkString
    editS10nNameDialogService.saveName(user, dialog, name.some).unsafeRunSync() shouldBe NameTooLong.invalidNec[String]
  }
}
