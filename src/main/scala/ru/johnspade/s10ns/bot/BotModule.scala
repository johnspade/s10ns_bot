package ru.johnspade.s10ns.bot

import cats.effect.Sync
import cats.~>

import doobie.free.connection.ConnectionIO
import telegramium.bots.high.Api

import ru.johnspade.s10ns.bot.engine.DefaultDialogEngine
import ru.johnspade.s10ns.bot.engine.TransactionalDialogEngine
import ru.johnspade.s10ns.exchangerates.ExchangeRatesModule
import ru.johnspade.s10ns.user.UserModule

final class BotModule[F[_], D[_]] private (
    val moneyService: MoneyService[F],
    val dialogEngine: TransactionalDialogEngine[F, D],
    val startController: StartController[F],
    val ignoreController: IgnoreController[F],
    val cbDataService: CbDataService[F],
    val userMiddleware: UserMiddleware[F, D]
)

object BotModule {
  def make[F[_]: Sync](
      userModule: UserModule[ConnectionIO],
      exchangeRatesModule: ExchangeRatesModule[F, ConnectionIO]
  )(implicit bot: Api[F], transact: ConnectionIO ~> F): F[BotModule[F, ConnectionIO]] =
    Sync[F].delay {
      val moneySrv     = new MoneyService[F](exchangeRatesModule.exchangeRatesService)
      val dialogEngine = new DefaultDialogEngine[F, ConnectionIO](userModule.userRepository)
      new BotModule[F, ConnectionIO](
        moneyService = moneySrv,
        dialogEngine = dialogEngine,
        startController = new StartController[F](dialogEngine),
        ignoreController = new IgnoreController[F],
        cbDataService = new CbDataService[F],
        userMiddleware = new UserMiddleware[F, ConnectionIO](userModule.userRepository)
      )
    }
}
