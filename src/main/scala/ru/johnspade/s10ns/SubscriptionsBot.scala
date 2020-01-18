package ru.johnspade.s10ns

import cats.effect.{Sync, Timer}
import cats.implicits._
import cats.{Monad, ~>}
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{TelegramUserOps, ackCb, sendReplyMessages, singleTextMessage}
import ru.johnspade.s10ns.bot.{Calendar, CbDataService, CreateS10nDialog, DefCurrency, Dialog, DropFirstPayment, EditS10n, EditS10nAmount, EditS10nAmountDialog, EditS10nBillingPeriod, EditS10nBillingPeriodDialog, EditS10nCurrency, EditS10nCurrencyDialog, EditS10nFirstPaymentDate, EditS10nFirstPaymentDateDialog, EditS10nName, EditS10nNameDialog, EditS10nOneTime, EditS10nOneTimeDialog, Errors, FirstPayment, Ignore, OneTime, PeriodUnit, RemoveS10n, S10n, S10ns, SettingsDialog, SkipIsOneTime, StartController}
import ru.johnspade.s10ns.calendar.CalendarController
import ru.johnspade.s10ns.settings.SettingsController
import ru.johnspade.s10ns.subscription.controller.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.client.Api
import telegramium.bots.high.LongPollBot
import telegramium.bots.{CallbackQuery, Message, User => TgUser}

class SubscriptionsBot[F[_] : Sync : Timer : Logger, D[_] : Monad](
  private val bot: Api[F],
  private val userRepo: UserRepository[D],
  private val s10nListController: SubscriptionListController[F, D],
  private val createS10nDialogController: CreateS10nDialogController[F, D],
  private val editS10nDialogController: EditS10nDialogController[F, D],
  private val calendarController: CalendarController[F],
  private val settingsController: SettingsController[F, D],
  private val startController: StartController[F, D],
  private val cbDataService: CbDataService[F]
)(private implicit val transact: D ~> F) extends LongPollBot[F](bot) {
  private implicit val api: Api[F] = bot

  override def onMessage(msg: Message): F[Unit] =
    msg.from.map { user =>
      routeMessage(msg.chat.id.toLong, user, msg)
    }
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logger[F].error(e)(e.getMessage))

  override def onCallbackQuery(query: CallbackQuery): F[Unit] = {
    val ackError = ackCb[F](query, Errors.Default.some)
    val tgUser = query.from.toUser(query.message.map(_.chat.id.toLong))

    def getUser = transact(userRepo.getOrCreate(tgUser))

    def route(data: String) =
      cbDataService.decode(data)
        .flatMap {
          case Ignore => ackCb[F](query)

          case s10ns: S10ns =>
            getUser.flatMap(s10nListController.subscriptionsCb(_, query, s10ns))

          case s10n: S10n =>
            getUser.flatMap(s10nListController.subscriptionCb(_, query, s10n))

          case billingPeriod: PeriodUnit =>
            getUser.flatMap { user =>
              user.dialog.collect {
                case d: CreateS10nDialog => createS10nDialogController.billingPeriodUnitCb(query, billingPeriod, user, d)
                case d: EditS10nOneTimeDialog => editS10nDialogController.s10nBillingPeriodCb(query, billingPeriod, user, d)
                case d: EditS10nBillingPeriodDialog => editS10nDialogController.s10nBillingPeriodCb(query, billingPeriod, user, d)
              }
                .getOrElse(ackError)
            }

          case SkipIsOneTime =>
            getUser.flatMap { user =>
              user.dialog.collect {
                case d: CreateS10nDialog => createS10nDialogController.skipIsOneTimeCb(query, user, d)
                case d: EditS10nOneTimeDialog => editS10nDialogController.removeS10nIsOneTimeCb(query, user, d)
              }
                .getOrElse(ackError)

            }

          case oneTime: OneTime =>
            getUser.flatMap { user =>
              user.dialog.collect {
                case d: CreateS10nDialog => createS10nDialogController.isOneTimeCb(query, oneTime, user, d)
                case d: EditS10nOneTimeDialog => editS10nDialogController.s10nOneTimeCb(query, oneTime, user, d)
              }
                .getOrElse(ackError)
            }

          case calendar: Calendar =>
            calendarController.calendarCb(query, calendar)

          case DropFirstPayment =>
            getUser.flatMap { user =>
              user.dialog.collect {
                case d: CreateS10nDialog => createS10nDialogController.skipFirstPaymentDateCb(query, user, d)
                case d: EditS10nFirstPaymentDateDialog =>
                  editS10nDialogController.removeFirstPaymentDateCb(query, user, d)
              }
                .getOrElse(ackError)
            }

          case firstPaymentDate: FirstPayment =>
            getUser.flatMap { user =>
              user.dialog.collect {
                case d: CreateS10nDialog => createS10nDialogController.firstPaymentDateCb(query, firstPaymentDate, user, d)
                case d: EditS10nFirstPaymentDateDialog =>
                  editS10nDialogController.s10nFirstPaymentDateCb(query, firstPaymentDate, user, d)
              }
                .getOrElse(ackError)
            }

          case removeS10n: RemoveS10n =>
            getUser.flatMap(s10nListController.removeSubscriptionCb(_, query, removeS10n))

          case editS10n: EditS10n =>
            s10nListController.editS10nCb(query, editS10n)

          case editS10nName: EditS10nName =>
            getUser.flatMap(editS10nDialogController.editS10nNameCb(_, query, editS10nName))

          case editS10nAmount: EditS10nAmount =>
            getUser.flatMap(editS10nDialogController.editS10nAmountCb(_, query, editS10nAmount))

          case editS10nCurrency: EditS10nCurrency =>
            getUser.flatMap(editS10nDialogController.editS10nCurrencyCb(_, query, editS10nCurrency))

          case editS10nOneTime: EditS10nOneTime =>
            getUser.flatMap(editS10nDialogController.editS10nOneTimeCb(_, query, editS10nOneTime))

          case editS10nBillingPeriod: EditS10nBillingPeriod =>
            getUser.flatMap(editS10nDialogController.editS10nBillingPeriodCb(_, query, editS10nBillingPeriod))

          case editS10nFirstPaymentDate: EditS10nFirstPaymentDate =>
            getUser.flatMap(editS10nDialogController.editS10nFirstPaymentDateCb(_, query, editS10nFirstPaymentDate))

          case DefCurrency =>
            getUser.flatMap(settingsController.defaultCurrencyCb(_, query))
        }

    query.data.map(route)
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logger[F].error(e)(e.getMessage))
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
        case d: EditS10nAmountDialog => editS10nDialogController.s10nEditAmountMessage(user, d, msg)
        case d: EditS10nCurrencyDialog => editS10nDialogController.s10nEditCurrencyMessage(user, d, msg)
        case d: EditS10nOneTimeDialog => editS10nDialogController.s10nBillingPeriodDurationMessage(user, d, msg)
        case d: EditS10nBillingPeriodDialog => editS10nDialogController.s10nBillingPeriodDurationMessage(user, d, msg)
        case _ => Sync[F].pure(singleTextMessage(Errors.Default))
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
              case t if t.startsWith("⚙️") => settingsController.settingsCommand.map(List(_))
              case _ => startController.helpCommand.map(List(_))
            }
          }
        }
    }

    val tgUser = from.toUser(chatId.some)
    transact(userRepo.getOrCreate(tgUser))
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
