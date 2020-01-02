package ru.johnspade.s10ns

import cats.Monad
import cats.effect.{Sync, Timer}
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.calendar.CalendarController
import ru.johnspade.s10ns.common.Errors
import ru.johnspade.s10ns.help.StartController
import ru.johnspade.s10ns.settings.SettingsController
import ru.johnspade.s10ns.subscription.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.telegram.TelegramOps.{TelegramUserOps, ackCb, sendReplyMessages, singleTextMessage}
import ru.johnspade.s10ns.telegram.{Calendar, CbDataService, DefCurrency, EditS10n, EditS10nAmount, EditS10nBillingPeriod, EditS10nName, EditS10nOneTime, FirstPayment, Ignore, OneTime, PeriodUnit, RemoveS10n, ReplyMessage, S10n, S10ns}
import ru.johnspade.s10ns.user.{CreateS10nDialog, Dialog, EditS10nAmountDialog, EditS10nBillingPeriodDialog, EditS10nNameDialog, EditS10nOneTimeDialog, SettingsDialog, User, UserRepository}
import telegramium.bots.client.Api
import telegramium.bots.high.LongPollBot
import telegramium.bots.{CallbackQuery, Message, User => TgUser}

class SubscriptionsBot[F[_] : Sync : Timer : Logger](
  private val bot: Api[F],
  private val userRepo: UserRepository,
  private val s10nListController: SubscriptionListController[F],
  private val createS10nDialogController: CreateS10nDialogController[F],
  private val editS10nDialogController: EditS10nDialogController[F],
  private val calendarController: CalendarController[F],
  private val settingsController: SettingsController[F],
  private val startController: StartController[F],
  private val cbDataService: CbDataService[F]
)(
  private implicit val xa: Transactor[F]
) extends LongPollBot[F](bot) {
  private implicit val api: Api[F] = bot

  override def onMessage(msg: Message): F[Unit] =
    msg.from.map { user =>
      routeMessage(msg.chat.id.toLong, user, msg)
    }
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logger[F].error(e)(e.getMessage))

  override def onCallbackQuery(query: CallbackQuery): F[Unit] = {
    val ackError = ackCb[F](query, Errors.default.some)

    def route(data: String, user: User) =
      cbDataService.decode(data)
        .flatMap {
          case Ignore => ackCb[F](query)

          case s10ns: S10ns =>
            s10nListController.subscriptionsCb(user, query, s10ns)

          case s10n: S10n =>
            s10nListController.subscriptionCb(user, query, s10n)

          case billingPeriod: PeriodUnit =>
            user.dialog.collect {
              case d: CreateS10nDialog => createS10nDialogController.billingPeriodUnitCb(query, billingPeriod, user, d)
              case d: EditS10nOneTimeDialog => editS10nDialogController.s10nBillingPeriodCb(query, billingPeriod, user, d)
              case d: EditS10nBillingPeriodDialog => editS10nDialogController.s10nBillingPeriodCb(query, billingPeriod, user, d)
            }
              .getOrElse(ackError)

          case oneTime: OneTime =>
            user.dialog.collect {
              case d: CreateS10nDialog => createS10nDialogController.isOneTimeCb(query, oneTime, user, d)
              case d: EditS10nOneTimeDialog => editS10nDialogController.s10nOneTimeCb(query, oneTime, user, d)
            }
              .getOrElse(ackError)

          case calendar: Calendar =>
            calendarController.calendarCb(query, calendar)

          case firstPaymentDate: FirstPayment =>
            user.dialog.collect {
              case d: CreateS10nDialog => createS10nDialogController.firstPaymentDateCb(query, firstPaymentDate, user, d)
            }
              .getOrElse(ackError)

          case removeS10n: RemoveS10n =>
            s10nListController.removeSubscriptionCb(user, query, removeS10n)

          case editS10n: EditS10n =>
            s10nListController.editS10nCb(query, editS10n)

          case editS10nName: EditS10nName =>
            editS10nDialogController.editS10nNameCb(user, query, editS10nName)

          case editS10nAmount: EditS10nAmount =>
            editS10nDialogController.editS10nAmountCb(user, query, editS10nAmount)

          case editS10nOneTime: EditS10nOneTime =>
            editS10nDialogController.editS10nOneTimeCb(user, query, editS10nOneTime)

          case editS10nBillingPeriod: EditS10nBillingPeriod =>
            editS10nDialogController.editS10nBillingPeriodCb(user, query, editS10nBillingPeriod)

          case DefCurrency =>
            settingsController.defaultCurrencyCb(user, query)
        }

    val tgUser = query.from.toUser(query.message.map(_.chat.id.toLong))
    userRepo.getOrCreate(tgUser)
      .transact(xa)
      .flatMap { user =>
        query.data.map(route(_, user))
          .getOrElse(Monad[F].unit)
          .handleErrorWith(e => Logger[F].error(e)(e.getMessage))
      }
  }

  private def routeMessage(chatId: Long, from: TgUser, message: Message): F[Unit] = {
    val msg = message.copy(text = message.text.map(_.trim))

    def handleCommands(user: User, text: String) =
      text match {
        case t if t.startsWith("/create") => createS10nDialogController.createCommand(user)
        case t if t.startsWith("/start") || t.startsWith("/reset") => startController.startCommand(user)
        case t if t.startsWith("/help") => startController.helpCommand
        case t if t.startsWith("/list") => s10nListController.listCommand(user)
        case t if t.startsWith("/settings") => settingsController.settingsCommand
        case _ => startController.helpCommand
      }

    def handleDialogs(user: User, dialog: Dialog) =
      dialog match {
        case d: CreateS10nDialog => createS10nDialogController.message(user, d, msg)
        case d: SettingsDialog => settingsController.message(user, d, msg)
        case d: EditS10nNameDialog => editS10nDialogController.s10nNameMessage(user, d, msg)
        case d: EditS10nAmountDialog => editS10nDialogController.s10nAmountMessage(user, d, msg)
        case d: EditS10nOneTimeDialog => editS10nDialogController.s10nBillingPeriodDurationMessage(user, d, msg)
        case d: EditS10nBillingPeriodDialog => editS10nDialogController.s10nBillingPeriodDurationMessage(user, d, msg)
        case _ => Sync[F].pure(singleTextMessage(Errors.default))
      }

    def handleText(user: User, text: String) = {
      user.dialog.map { dialog =>
        if (text.startsWith("/")) {
          if (importantCommands.exists(text.startsWith))
            handleCommands(user, text).map(List(_))
          else
            Sync[F].pure(List(ReplyMessage("Cannot execute command. Use /reset to stop.")))
        }
        else
          handleDialogs(user, dialog)
      }
        .getOrElse {
          if (text.startsWith("/"))
            handleCommands(user, text).map(List(_))
          else {
            text match {
              case t if t.startsWith("\uD83D\uDCCB") => s10nListController.listCommand(user).map(List(_))
              case t if t.startsWith("\uD83D\uDCB2") => createS10nDialogController.createCommand(user).map(List(_))
              case t if t.startsWith("➕") => createS10nDialogController.createWithDefaultCurrencyCommand(user).map(List(_))
              case _ => startController.helpCommand.map(List(_))
            }
          }
        }
    }

    val tgUser = from.toUser(chatId.some)
    userRepo.getOrCreate(tgUser)
      .transact(xa)
      .flatMap { user =>
        msg.text match {
          case Some(txt) =>
            handleText(user, txt)
              .flatMap {
                sendReplyMessages[F](msg, _)
              }
          case None => Monad[F].unit
        }
      }
  }

  private val importantCommands = Seq("/start", "/reset")
}
