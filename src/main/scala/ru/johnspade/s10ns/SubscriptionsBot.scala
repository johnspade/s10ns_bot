package ru.johnspade.s10ns

import cats.effect.{Sync, Timer}
import cats.implicits._
import cats.{Monad, Parallel, ~>}
import ru.johnspade.s10ns.bot.engine.ReplyMessage
import ru.johnspade.s10ns.bot.engine.TelegramOps.{TelegramUserOps, sendReplyMessages, singleTextMessage}
import ru.johnspade.s10ns.bot.engine.callbackqueries.{CallbackDataDecoder, CallbackQueryHandler, DecodeError, ParseError}
import ru.johnspade.s10ns.bot.{CbData, CbDataService, CreateS10nDialog, Dialog, EditS10nAmountDialog, EditS10nBillingPeriodDialog, EditS10nCurrencyDialog, EditS10nNameDialog, EditS10nOneTimeDialog, Errors, IgnoreController, SettingsDialog, StartController, UserMiddleware}
import ru.johnspade.s10ns.calendar.CalendarController
import ru.johnspade.s10ns.settings.SettingsController
import ru.johnspade.s10ns.subscription.controller.{S10nController, SubscriptionListController}
import ru.johnspade.s10ns.user.{User, UserRepository}
import telegramium.bots.high.{Api, LongPollBot}
import telegramium.bots.{CallbackQuery, Message, User => TgUser}
import tofu.logging.{Logging, Logs}

class SubscriptionsBot[F[_]: Sync: Timer: Parallel: Logging, D[_]: Monad](
  private val userRepo: UserRepository[D],
  private val s10nListController: SubscriptionListController[F],
  private val s10nController: S10nController[F],
  private val calendarController: CalendarController[F],
  private val settingsController: SettingsController[F],
  private val startController: StartController[F],
  private val ignoreController: IgnoreController[F],
  private val cbDataService: CbDataService[F],
  private val userMiddleware: UserMiddleware[F, D]
)(private implicit val api: Api[F], val transact: D ~> F) extends LongPollBot[F](api) {

  override def onMessage(msg: Message): F[Unit] =
    msg.from.map { user =>
      routeMessage(msg.chat.id, user, msg)
    }
      .getOrElse(Monad[F].unit)
      .handleErrorWith(e => Logging[F].errorCause(e.getMessage, e))

  private val simpleRoutes = calendarController.routes <+> ignoreController.routes
  private val userRoutes = s10nListController.routes <+> s10nController.routes <+> settingsController.routes
  private val allRoutes = simpleRoutes <+> userMiddleware.userEnricher(userRoutes)

  private val cbDataDecoder: CallbackDataDecoder[F, CbData] =
    cbDataService.decode(_).left.map {
      case error: kantan.csv.ParseError => ParseError(error.getMessage)
      case error: kantan.csv.DecodeError => DecodeError(error.getMessage)
    }
      .toEitherT[F]

  override def onCallbackQuery(query: CallbackQuery): F[Unit] =
    CallbackQueryHandler.handle[F, CbData](
      query,
      allRoutes,
      cbDataDecoder,
      _ => Monad[F].unit
    )
      .handleErrorWith(e => Logging[F].errorCause(e.getMessage, e))

  private def routeMessage(chatId: Long, from: TgUser, message: Message): F[Unit] = {
    val msg = message.copy(text = message.text.map(_.trim))

    def handleCommands(user: User, text: String): F[List[ReplyMessage]] =
      text match {
        case t if t.startsWith("/create") => s10nController.createCommand(user)
        case t if t.startsWith("/start") || t.startsWith("/reset") => startController.startCommand(user).map(List(_))
        case t if t.startsWith("/help") => startController.helpCommand.map(List(_))
        case t if t.startsWith("/list") => s10nListController.listCommand(user).map(List(_))
        case t if t.startsWith("/settings") => settingsController.settingsCommand.map(List(_))
        case _ => startController.helpCommand.map(List(_))
      }

    def handleDialogs(user: User, dialog: Dialog) =
      dialog match {
        case d: CreateS10nDialog => s10nController.message(user, d, msg)
        case d: SettingsDialog => settingsController.message(user, d, msg)
        case d: EditS10nNameDialog => s10nController.s10nNameMessage(user, d, msg)
        case d: EditS10nAmountDialog => s10nController.s10nEditAmountMessage(user, d, msg)
        case d: EditS10nCurrencyDialog => s10nController.s10nEditCurrencyMessage(user, d, msg)
        case d: EditS10nOneTimeDialog => s10nController.s10nBillingPeriodDurationMessage(user, d, msg)
        case d: EditS10nBillingPeriodDialog => s10nController.s10nBillingPeriodDurationMessage(user, d, msg)
        case _ => Sync[F].pure(singleTextMessage(Errors.UseInlineKeyboard))
      }

    def handleText(user: User, text: String): F[List[ReplyMessage]] = {
      user.dialog.map { dialog =>
        if (text.startsWith("/")) {
          if (importantCommands.exists(text.startsWith))
            handleCommands(user, text)
          else
            Sync[F].pure(List(ReplyMessage("Cannot execute this command. Use /reset to stop.")))
        }
        else
          handleDialogs(user, dialog)
      }
        .getOrElse {
          if (text.startsWith("/"))
            handleCommands(user, text)
          else {
            text match {
              case t if t.startsWith("\uD83D\uDCCB") => s10nListController.listCommand(user).map(List(_))
              case t if t.startsWith("\uD83D\uDCB2") => s10nController.createCommand(user)
              case t if t.startsWith("➕") => s10nController.createWithDefaultCurrencyCommand(user)
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

object SubscriptionsBot {
  def apply[F[_]: Sync: Timer: Parallel, D[_]: Monad](
    userRepo: UserRepository[D],
    s10nListController: SubscriptionListController[F],
    s10nController: S10nController[F],
    calendarController: CalendarController[F],
    settingsController: SettingsController[F],
    startController: StartController[F],
    ignoreController: IgnoreController[F],
    cbDataService: CbDataService[F],
    userMiddleware: UserMiddleware[F, D]
  )(implicit api: Api[F], transact: D ~> F, logs: Logs[F, F]): F[SubscriptionsBot[F, D]] =
    logs.forService[SubscriptionsBot[F, D]].map { implicit l =>
      new SubscriptionsBot[F, D](
        userRepo,
        s10nListController,
        s10nController,
        calendarController,
        settingsController,
        startController,
        ignoreController,
        cbDataService,
        userMiddleware
      )
    }
}
