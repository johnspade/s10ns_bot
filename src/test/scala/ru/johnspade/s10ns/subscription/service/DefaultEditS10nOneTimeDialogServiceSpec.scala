package ru.johnspade.s10ns.subscription.service

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.option._
import cats.syntax.validated._

import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.scalatest.DiffShouldMatcher
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import telegramium.bots.Markdown
import telegramium.bots.ReplyKeyboardRemove

import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.EditS10nOneTime
import ru.johnspade.s10ns.bot.EditS10nOneTimeDialog
import ru.johnspade.s10ns.bot.Markup
import ru.johnspade.s10ns.bot.NotANumber
import ru.johnspade.s10ns.bot.NumberMustBePositive
import ru.johnspade.s10ns.bot.OneTime
import ru.johnspade.s10ns.bot.PeriodUnit
import ru.johnspade.s10ns.bot.TextCannotBeEmpty
import ru.johnspade.s10ns.bot.engine.DefaultMsgService
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.subscription.BillingPeriod
import ru.johnspade.s10ns.subscription.BillingPeriodUnit
import ru.johnspade.s10ns.subscription.dialog.EditS10nOneTimeDialogState
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10nOneTimeDialogService

class DefaultEditS10nOneTimeDialogServiceSpec
    extends AnyFlatSpec
    with MockFactory
    with EditS10nDialogServiceSpec
    with Matchers
    with DiffShouldMatcher {
  private val editS10nOneTimeDialogService = new DefaultEditS10nOneTimeDialogService(
    s10nsListMessageService,
    new DefaultMsgService[IO, EditS10nOneTimeDialogState],
    mockUserRepo,
    mockS10nRepo,
    dialogEngine
  )

  "onEditS10nOneTimeCb" should "ask is it an one time subscription" in {
    val dialog = EditS10nOneTimeDialog(EditS10nOneTimeDialogState.IsOneTime, s10n)
    (mockS10nRepo.getById _).expects(s10nId).returns(s10n.some)
    val updatedUser = user.copy(dialog = dialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val result = editS10nOneTimeDialogService.onEditS10nOneTimeCb(user, EditS10nOneTime(s10nId)).unsafeRunSync()
    result shouldMatchTo {
      List(
        ReplyMessage("Recurring/one time:", ReplyKeyboardRemove(removeKeyboard = true).some),
        ReplyMessage("\uD83D\uDD18/☑️", Markup.isOneTimeReplyMarkup("Do not fill (remove)").some)
      )
    }
  }

  behavior of "saveIsOneTime"

  it should "just save a subscription when oneTime=true and remove a billing period" in {
    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.IsOneTime,
      s10n.copy(billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some)
    )
    val updatedS10n = s10n.copy(
      oneTime = true.some,
      billingPeriod = None
    )
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    editS10nOneTimeDialogService
      .saveIsOneTime(OneTime(true), user, dialog)
      .unsafeRunSync() shouldBe
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

  it should "just save a subscription with a billing period when oneTime=false" in {
    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.IsOneTime,
      s10n.copy(billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some)
    )
    val updatedS10n = s10n.copy(
      oneTime = false.some,
      billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
    )
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    editS10nOneTimeDialogService
      .saveIsOneTime(OneTime(false), user, dialog)
      .unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |0.00 €
              |
              |_Billing period:_ every 1 day""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      )
  }

  it should "ask for a billing period unit when oneTime=false" in {
    val updatedDialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodUnit,
      s10n.copy(oneTime = false.some)
    )
    val updatedUser = user.copy(dialog = updatedDialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val dialog = EditS10nOneTimeDialog(EditS10nOneTimeDialogState.IsOneTime, s10n)
    editS10nOneTimeDialogService
      .saveIsOneTime(OneTime(false), user, dialog)
      .unsafeRunSync() shouldBe
      List(ReplyMessage("Billing period unit:", Markup.BillingPeriodUnitReplyMarkup.some))
  }

  "removeIsOneTime" should "just save a subscription" in {
    (mockS10nRepo.update _).expects(s10n).returns(s10n.some)

    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.IsOneTime,
      s10n.copy(oneTime = true.some)
    )
    editS10nOneTimeDialogService.removeIsOneTime(user, dialog).unsafeRunSync() shouldBe
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

  "saveEveryMonth" should "just save a subscription" in {
    val updatedS10n = s10n.copy(
      oneTime = false.some,
      billingPeriod = BillingPeriod(1, BillingPeriodUnit.Month).some
    )
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    val dialog = EditS10nOneTimeDialog(EditS10nOneTimeDialogState.IsOneTime, s10n)
    editS10nOneTimeDialogService.saveEveryMonth(user, dialog).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |0.00 €
              |
              |_Billing period:_ every 1 month""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      )
  }

  "saveBillingPeriodUnit" should "ask for a billing period duration" in {
    val updatedUser = user.copy(dialog =
      EditS10nOneTimeDialog(
        EditS10nOneTimeDialogState.BillingPeriodDuration,
        s10n.copy(
          oneTime = false.some,
          billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
        )
      ).some
    )
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodUnit,
      s10n.copy(oneTime = false.some)
    )
    editS10nOneTimeDialogService
      .saveBillingPeriodUnit(PeriodUnit(BillingPeriodUnit.Day), user, dialog)
      .unsafeRunSync() shouldBe
      List(ReplyMessage("Billing period duration (1, 2...):"))
  }

  behavior of "saveBillingPeriodDuration"

  it should "save a subscription with a new billing period" in {
    val updatedS10n = s10n.copy(
      oneTime = false.some,
      billingPeriod = BillingPeriod(3, BillingPeriodUnit.Day).some
    )
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodDuration,
      s10n.copy(
        oneTime = false.some,
        billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
      )
    )
    editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, "3".some).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          s"""|*Name*
              |
              |0.00 €
              |
              |_Billing period:_ every 3 days""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      ).validNec
  }

  it should "fail if a text is missing" in {
    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodDuration,
      s10n.copy(
        oneTime = false.some,
        billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
      )
    )
    editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, None).unsafeRunSync() shouldBe
      TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a duration is not a number" in {
    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodDuration,
      s10n.copy(
        oneTime = false.some,
        billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
      )
    )
    editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, "NaN".some).unsafeRunSync() shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if a duration is not positive" in {
    val dialog = EditS10nOneTimeDialog(
      EditS10nOneTimeDialogState.BillingPeriodDuration,
      s10n.copy(
        oneTime = false.some,
        billingPeriod = BillingPeriod(1, BillingPeriodUnit.Day).some
      )
    )
    editS10nOneTimeDialogService.saveBillingPeriodDuration(user, dialog, "-3".some).unsafeRunSync() shouldBe
      NumberMustBePositive.invalidNec[Int]
  }
}
