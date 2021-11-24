package ru.johnspade.s10ns.subscription.service

import cats.effect.IO
import cats.syntax.option._
import cats.syntax.validated._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.{DefaultMsgService, ReplyMessage}
import ru.johnspade.s10ns.bot.{EditS10nAmount, EditS10nAmountDialog, NotANumber, NumberMustBePositive, TextCannotBeEmpty}
import ru.johnspade.s10ns.subscription.dialog.EditS10nAmountDialogState
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10nAmountDialogService
import telegramium.bots.{Markdown, ReplyKeyboardRemove}
import com.softwaremill.diffx.generic.auto._
import cats.effect.unsafe.implicits.global

class DefaultEditS10nAmountDialogServiceSpec extends AnyFlatSpec with EditS10nDialogServiceSpec with Matchers with DiffShouldMatcher {
  private val editS10nAmountDialogService = new DefaultEditS10nAmountDialogService(
    s10nsListMessageService,
    new DefaultMsgService[IO, EditS10nAmountDialogState],
    mockUserRepo,
    mockS10nRepo,
    dialogEngine
  )
  private val dialog = EditS10nAmountDialog(EditS10nAmountDialogState.Amount, s10n)

  "onEditS10nAmountCb" should "ask for a new subscription's amount" in {
    (mockS10nRepo.getById _).expects(s10nId).returns(s10n.some)
    val updatedUser = user.copy(dialog = dialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val result = editS10nAmountDialogService.onEditS10nAmountCb(user, EditS10nAmount(s10nId)).unsafeRunSync()
    result shouldMatchTo {
      List(ReplyMessage("Amount:", ReplyKeyboardRemove(removeKeyboard = true).some))
    }
  }

  behavior of "saveAmount"

  it should "save a subscription with new amount" in {
    val updatedS10n = s10n.copy(amount = Money.of(CurrencyUnit.EUR, 13.37))
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    editS10nAmountDialogService.saveAmount(user, dialog, "13.37".some).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |13.37 €
              |""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      ).validNec
  }

  it should "fail if a text is missing" in {
    editS10nAmountDialogService.saveAmount(user, dialog, None).unsafeRunSync() shouldBe TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if an amount is not a number" in {
    editS10nAmountDialogService.saveAmount(user, dialog, "NaN".some).unsafeRunSync() shouldBe NotANumber.invalidNec[String]
  }

  it should "fail if an amount is not positive" in {
    editS10nAmountDialogService.saveAmount(user, dialog, "-3".some).unsafeRunSync() shouldBe NumberMustBePositive.invalidNec[BigDecimal]
  }
}
