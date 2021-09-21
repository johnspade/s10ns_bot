package ru.johnspade.s10ns.subscription

import cats.effect.{Clock, Sync}
import cats.implicits._
import cats.{Defer, ~>}
import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.bot.BotModule
import ru.johnspade.s10ns.bot.engine.DefaultMsgService
import ru.johnspade.s10ns.calendar.CalendarModule
import ru.johnspade.s10ns.subscription.controller.{S10nController, SubscriptionListController}
import ru.johnspade.s10ns.subscription.dialog.{CreateS10nMsgService, EditS10n1stPaymentDateMsgService, EditS10nAmountDialogState, EditS10nBillingPeriodDialogState, EditS10nCurrencyDialogState, EditS10nNameDialogState, EditS10nOneTimeDialogState}
import ru.johnspade.s10ns.subscription.repository.{DoobieSubscriptionRepository, SubscriptionRepository}
import ru.johnspade.s10ns.subscription.service.impl.{DefaultCreateS10nDialogFsmService, DefaultCreateS10nDialogService, DefaultEditS10n1stPaymentDateDialogService, DefaultEditS10nAmountDialogService, DefaultEditS10nBillingPeriodDialogService, DefaultEditS10nCurrencyDialogService, DefaultEditS10nNameDialogService, DefaultEditS10nOneTimeDialogService, DefaultSubscriptionListService}
import ru.johnspade.s10ns.subscription.service.{S10nInfoService, S10nsListMessageService, S10nsListReplyMessageService}
import ru.johnspade.s10ns.user.UserModule
import telegramium.bots.high.Api
import tofu.logging.Logs
import cats.effect.Temporal

final class SubscriptionModule[F[_], D[_]](
  val subscriptionRepository: SubscriptionRepository[D],
  val editS10nDialogController: S10nController[F],
  val subscriptionListController: SubscriptionListController[F],
  val s10nInfoService: S10nInfoService[F],
  val s10nsListMessageService: S10nsListMessageService[F]
)

object SubscriptionModule {
  def make[F[_]: Clock: Temporal: Defer](
    userModule: UserModule[ConnectionIO],
    botModule: BotModule[F, ConnectionIO],
    calendarModule: CalendarModule[F]
  )(implicit bot: Api[F], transact: ConnectionIO ~> F, logs: Logs[F, F]): F[SubscriptionModule[F, ConnectionIO]] = {
    import botModule.{dialogEngine, moneyService}
    import calendarModule.calendarService
    import userModule.userRepository

    val subscriptionRepo = new DoobieSubscriptionRepository
    val s10nInfoSrv = new S10nInfoService[F]
    val s10nsListReplyMessageService = new S10nsListReplyMessageService
    val s10nsListMessageSrv = new S10nsListMessageService(moneyService, s10nInfoSrv, s10nsListReplyMessageService)
    val createS10nMsgSrv = new CreateS10nMsgService[F](calendarService)
    val createS10nDialogFsmSrv = new DefaultCreateS10nDialogFsmService[F, ConnectionIO](
      subscriptionRepo,
      userRepository,
      botModule.dialogEngine,
      s10nsListMessageSrv,
      createS10nMsgSrv
    )
    val createS10nDialogSrv = new DefaultCreateS10nDialogService[F, ConnectionIO](
      createS10nDialogFsmSrv,
      createS10nMsgSrv,
      botModule.dialogEngine
    )
    val editS10n1stPaymentDateDialogService = new DefaultEditS10n1stPaymentDateDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new EditS10n1stPaymentDateMsgService[F](calendarService), userRepository, subscriptionRepo, dialogEngine
    )
    val editS10nNameDialogService = new DefaultEditS10nNameDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new DefaultMsgService[F, EditS10nNameDialogState], userRepository, subscriptionRepo, dialogEngine
    )
    val editS10nAmountDialogService = new DefaultEditS10nAmountDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new DefaultMsgService[F, EditS10nAmountDialogState], userRepository, subscriptionRepo, dialogEngine
    )
    val editS10nBillingPeriodDialogService = new DefaultEditS10nBillingPeriodDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new DefaultMsgService[F, EditS10nBillingPeriodDialogState], userRepository, subscriptionRepo, dialogEngine
    )
    val editS10nCurrencyDialogService = new DefaultEditS10nCurrencyDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new DefaultMsgService[F, EditS10nCurrencyDialogState], userRepository, subscriptionRepo, dialogEngine
    )
    val editS10nOneTimeDialogService = new DefaultEditS10nOneTimeDialogService[F, ConnectionIO](
      s10nsListMessageSrv, new DefaultMsgService[F, EditS10nOneTimeDialogState], userRepository, subscriptionRepo, dialogEngine
    )
    val s10nsListSrv = new DefaultSubscriptionListService[F, ConnectionIO](subscriptionRepo, s10nsListMessageSrv)
    val subscriptionListController = new SubscriptionListController[F](s10nsListSrv)
    for {
      s10nController <- S10nController(
        createS10nDialogSrv,
        editS10n1stPaymentDateDialogService,
        editS10nNameDialogService,
        editS10nAmountDialogService,
        editS10nBillingPeriodDialogService,
        editS10nCurrencyDialogService,
        editS10nOneTimeDialogService
      )
    } yield new SubscriptionModule[F, ConnectionIO] (
      subscriptionRepository = subscriptionRepo,
      editS10nDialogController = s10nController,
      subscriptionListController = subscriptionListController,
      s10nInfoService = s10nInfoSrv,
      s10nsListMessageService = s10nsListMessageSrv
    )
  }
}
