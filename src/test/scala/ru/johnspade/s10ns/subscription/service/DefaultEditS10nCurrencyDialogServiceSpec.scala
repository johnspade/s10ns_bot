package ru.johnspade.s10ns.subscription.service

import cats.effect.IO
import cats.syntax.option._
import cats.syntax.validated._
import com.softwaremill.diffx.scalatest.DiffMatcher
import org.joda.money.{CurrencyUnit, Money}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.johnspade.s10ns.TestTransactor.transact
import ru.johnspade.s10ns.bot.engine.{DefaultMsgService, ReplyMessage}
import ru.johnspade.s10ns.bot.{EditS10nCurrency, EditS10nCurrencyDialog, Markup, NotANumber, NumberMustBePositive, TextCannotBeEmpty, UnknownCurrency}
import ru.johnspade.s10ns.subscription.dialog.EditS10nCurrencyDialogState
import ru.johnspade.s10ns.subscription.service.impl.DefaultEditS10nCurrencyDialogService
import telegramium.bots.Markdown

class DefaultEditS10nCurrencyDialogServiceSpec extends AnyFlatSpec with EditS10nDialogServiceSpec with Matchers with DiffMatcher {
  private val editS10nCurrencyDialogService = new DefaultEditS10nCurrencyDialogService(
    s10nsListMessageService,
    new DefaultMsgService[IO, EditS10nCurrencyDialogState],
    mockUserRepo,
    mockS10nRepo,
    dialogEngine
  )

  "onEditS10nCurrencyCb" should "ask for a new subscription's currency" in {
    (mockS10nRepo.getById _).expects(s10nId).returns(s10n.some)
    val dialog = EditS10nCurrencyDialog(EditS10nCurrencyDialogState.Currency, s10n)
    val updatedUser = user.copy(dialog = dialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    val result = editS10nCurrencyDialogService.onEditS10nCurrencyCb(user, EditS10nCurrency(s10nId)).unsafeRunSync()
    result should matchTo {
      List(ReplyMessage("Enter or select the currency code:", Markup.CurrencyReplyMarkup.some))
    }
  }

  behavior of "saveCurrency"

  private val dialogWithCurrencyState = EditS10nCurrencyDialog(EditS10nCurrencyDialogState.Currency, s10n)

  it should "save a subscription with new currency and ask for new amount" in {
    val updatedDialog = EditS10nCurrencyDialog(
      EditS10nCurrencyDialogState.Amount,
      s10n.copy(amount = Money.zero(CurrencyUnit.USD))
    )
    val updatedUser = user.copy(dialog = updatedDialog.some)
    (mockUserRepo.createOrUpdate _).expects(updatedUser).returns(updatedUser)

    editS10nCurrencyDialogService.saveCurrency(user, dialogWithCurrencyState, "USD".some).unsafeRunSync() shouldBe
      List(ReplyMessage("Amount:")).validNec
  }

  it should "fail if a text is missing" in {
    editS10nCurrencyDialogService.saveCurrency(user, dialogWithCurrencyState, None).unsafeRunSync() shouldBe
      TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if a currency's code is unknown" in {
    editS10nCurrencyDialogService.saveCurrency(user, dialogWithCurrencyState, "AAA".some).unsafeRunSync() shouldBe
      UnknownCurrency.invalidNec[CurrencyUnit]
  }

  behavior of "saveAmount"

  private val dialogWithAmountState = EditS10nCurrencyDialog(
    EditS10nCurrencyDialogState.Amount,
    s10n.copy(amount = Money.zero(CurrencyUnit.USD))
  )

  it should "save a subscription with new amount" in {
    val updatedS10n = s10n.copy(amount = Money.of(CurrencyUnit.USD, 13.37))
    (mockS10nRepo.update _).expects(updatedS10n).returns(updatedS10n.some)

    editS10nCurrencyDialogService.saveAmount(user, dialogWithAmountState, "13.37".some).unsafeRunSync() shouldBe
      List(
        defaultSavedMessage,
        ReplyMessage(
          """|*Name*
             |
             |13.37 $
             |≈11.97 €
             |""".stripMargin,
          defaultS10nMarkup.some,
          Markdown.some
        )
      ).validNec
  }

  it should "fail if a text is missing" in {
    editS10nCurrencyDialogService.saveAmount(user, dialogWithAmountState, None).unsafeRunSync() shouldBe
      TextCannotBeEmpty.invalidNec[String]
  }

  it should "fail if an amount is not a number" in {
    editS10nCurrencyDialogService.saveAmount(user, dialogWithAmountState, "NaN".some).unsafeRunSync() shouldBe
      NotANumber.invalidNec[String]
  }

  it should "fail if an amount is not positive" in {
    editS10nCurrencyDialogService.saveAmount(user, dialogWithAmountState, "-3".some).unsafeRunSync() shouldBe
      NumberMustBePositive.invalidNec[BigDecimal]
  }
}
