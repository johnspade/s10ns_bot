package ru.johnspade.s10ns.subscription.service

import java.time.LocalDate
import java.time.ZoneOffset

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option._

import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.Markdown
import telegramium.bots.ReplyKeyboardRemove

import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDate
import ru.johnspade.s10ns.bot.EditS10nFirstPaymentDateDialog
import ru.johnspade.s10ns.bot.FirstPayment
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.calendar.CalendarService
import ru.johnspade.s10ns.subscription.dialog.EditS10n1stPaymentDateMsgService
import ru.johnspade.s10ns.subscription.dialog.EditS10nFirstPaymentDateDialogState
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10n1stPaymentDateDialogService

class DefaultEditS10n1stPaymentDateDialogServiceSpec
    extends AnyFlatSpec
    with MockFactory
    with EditS10nDialogServiceSpec
    with Matchers
    with DiffShouldMatcher {
  private val calendarService = new CalendarService
  private val editS10n1stPaymentDateDialogService = new DefaultEditS10n1stPaymentDateDialogService(
    s10nsListMessageService,
    new EditS10n1stPaymentDateMsgService[IO](calendarService),
    mockUserRepo,
    mockS10nRepo,
    dialogEngine
  )

  "onEditS10nFirstPaymentDateCb" should "ask for a new subscriptions first date" in {
    (mockS10nRepo.getById _).expects(s10nId).returns(s10n.some)
    val dialog      = EditS10nFirstPaymentDateDialog(EditS10nFirstPaymentDateDialogState.FirstPaymentDate, s10n)
    val updatedUser = user.copy(dialog = dialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val result = editS10n1stPaymentDateDialogService
      .onEditS10nFirstPaymentDateCb(user, EditS10nFirstPaymentDate(s10nId))
      .unsafeRunSync()
    result shouldMatchTo {
      List(
        ReplyMessage("First payment date:", ReplyKeyboardRemove(removeKeyboard = true).some),
        ReplyMessage("\uD83D\uDD18/☑️", calendarService.generateDaysKeyboard(LocalDate.now(ZoneOffset.UTC)).some)
      )
    }
  }

  "saveFirstPaymentDate" should "just save a subscription" in {
    val date        = LocalDate.of(2020, 10, 8)
    val updatedS10n = s10n.copy(firstPaymentDate = date.some)
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    val dialog = EditS10nFirstPaymentDateDialog(EditS10nFirstPaymentDateDialogState.FirstPaymentDate, s10n)
    editS10n1stPaymentDateDialogService.saveFirstPaymentDate(FirstPayment(date), user, dialog).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |0.00 €
              |
              |_First payment:_ 2020-10-08""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      )
  }

  "removeFirstPaymentDate" should "just save a subscription" in {
    (mockS10nRepo.update _).expects(s10n).returns(s10n.some)

    val dialog = EditS10nFirstPaymentDateDialog(
      EditS10nFirstPaymentDateDialogState.FirstPaymentDate,
      s10n.copy(firstPaymentDate = LocalDate.of(2020, 10, 8).some)
    )

    editS10n1stPaymentDateDialogService.removeFirstPaymentDate(user, dialog).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |0.00 €
              |""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      )
  }
}
