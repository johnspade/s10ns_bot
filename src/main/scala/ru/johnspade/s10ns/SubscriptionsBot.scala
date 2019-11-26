package ru.johnspade.s10ns

import cats.Monad
import cats.effect.{Sync, Timer}
import cats.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.Logger
import ru.johnspade.s10ns.calendar.CalendarController
import ru.johnspade.s10ns.help.StartController
import ru.johnspade.s10ns.settings.SettingsController
import ru.johnspade.s10ns.subscription.{CreateS10nDialogController, EditS10nDialogController, SubscriptionListController}
import ru.johnspade.s10ns.telegram.CbData._
import ru.johnspade.s10ns.telegram.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.telegram.{BillingPeriodUnitCbData, CalendarCbData, CbDataService, DefaultCurrencyCbData, EditS10nCbData, EditS10nNameCbData, FirstPaymentDateCbData, IgnoreCbData, IsOneTimeCbData, RemoveSubscriptionCbData, ReplyMessage, SubscriptionCbData, SubscriptionsCbData}
import ru.johnspade.s10ns.user.{DialogType, User, UserRepository}
import telegramium.bots.client.{Api, SendMessageReq}
import telegramium.bots.high.LongPollBot
import telegramium.bots.{CallbackQuery, ChatIntId, Message, User => TgUser}
import ru.johnspade.s10ns.telegram.TelegramOps.ackCb

class SubscriptionsBot[F[_] : Sync : Timer : Logger](
  private val bot: Api[F],
  private val userRepo: UserRepository,
  private val s10nListController: SubscriptionListController[F],
  private val createS10nDialogController: CreateS10nDialogController[F],
  private val editS10nDialogController: EditS10nDialogController[F],
  private val calendarController: CalendarController[F],
  private val settingsController: SettingsController[F],
  private val startController: StartController[F],
  private val xa: Transactor[F],
  private val cbDataService: CbDataService[F]
)
  extends LongPollBot[F](bot) {
  private implicit val api: Api[F] = bot

  override def onMessage(msg: Message): F[Unit] =
    msg.from.map { user =>
      routeMessage(msg.chat.id.toLong, user, msg)
    }
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logger[F].error(e)(e.getMessage))

  override def onCallbackQuery(query: CallbackQuery): F[Unit] = {
    def route(data: String) =
      cbDataService.decode(data)
        .flatMap {
          case _: IgnoreCbData => ackCb(query)

          case s10ns: SubscriptionsCbData =>
            s10nListController.subscriptionsCb(query, s10ns)

          case s10n: SubscriptionCbData =>
            s10nListController.subscriptionCb(query, s10n)

          case billingPeriod: BillingPeriodUnitCbData =>
            createS10nDialogController.billingPeriodUnitCb(query, billingPeriod)

          case oneTime: IsOneTimeCbData =>
            createS10nDialogController.isOneTimeCb(query, oneTime)

          case calendar: CalendarCbData =>
            calendarController.calendarCb(query, calendar)

          case firstPaymentDate: FirstPaymentDateCbData =>
            createS10nDialogController.firstPaymentDateCb(query, firstPaymentDate)

          case removeS10n: RemoveSubscriptionCbData =>
            s10nListController.removeSubscriptionCb(query, removeS10n)

          case editS10n: EditS10nCbData =>
            s10nListController.editS10nCb(query, editS10n)

          case editS10nName: EditS10nNameCbData =>
            editS10nDialogController.editS10nNameCb(query, editS10nName)

          case _: DefaultCurrencyCbData =>
            settingsController.defaultCurrencyCb(query)
        }

    query.data.map(route)
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logger[F].error(e)(e.getMessage))
  }

  private def routeMessage(chatId: Long, from: TgUser, message: Message): F[Unit] = {
    import doobie.implicits._

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

    def handleDialogs(user: User, dialogType: DialogType) =
      dialogType match {
        case DialogType.CreateSubscription => createS10nDialogController.message(user, msg)
        case DialogType.Settings => settingsController.message(user, msg)
        case DialogType.EditS10nName => editS10nDialogController.s10nNameMessage(user, msg)
      }

    def handleText(user: User, text: String) = {
      user.dialogType
        .map { dialogType =>
          if (text.startsWith("/")) {
            if (importantCommands.exists(text.startsWith))
              handleCommands(user, text)
            else
              Sync[F].pure(ReplyMessage("Cannot execute command. Use /reset to stop."))
          }
          else
            handleDialogs(user, dialogType)
        }
        .getOrElse {
          if (text.startsWith("/"))
            handleCommands(user, text)
          else {
            text match {
              case t if t.startsWith("\uD83D\uDCCB") => s10nListController.listCommand(user)
              case t if t.startsWith("\uD83D\uDCB2") => createS10nDialogController.createCommand(user)
              case t if t.startsWith("➕") => createS10nDialogController.createWithDefaultCurrencyCommand(user)
              case _ => startController.helpCommand
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
              .flatMap { reply =>
                bot.sendMessage(SendMessageReq(
                  chatId = ChatIntId(msg.chat.id),
                  text = reply.text,
                  replyMarkup = reply.markup
                ))
                  .void
                  .handleErrorWith(e => Logger[F].error(e)(e.getMessage))
              }
          case None => Monad[F].unit
        }
      }
  }

  private val importantCommands = Seq("/start", "/reset")
}
