package ru.johnspade.s10ns.bot

import cats.effect.Sync
import cats.~>
import doobie.free.connection.ConnectionIO
import ru.johnspade.s10ns.bot.engine.{DefaultDialogEngine, TransactionalDialogEngine}
import ru.johnspade.s10ns.exchangerates.ExchangeRatesModule
import ru.johnspade.s10ns.user.UserModule

final class BotModule[F[_], D[_]] private(
  val moneyService: MoneyService[F],
  val dialogEngine: TransactionalDialogEngine[F, D],
  val startController: StartController[F],
  val cbDataService: CbDataService[F]
)

object BotModule {
  def make[F[_]: Sync](
    userModule: UserModule[ConnectionIO],
    exchangeRatesModule: ExchangeRatesModule[F, ConnectionIO]
  )(implicit transact: ConnectionIO ~> F): F[BotModule[F, ConnectionIO]] =
    Sync[F].delay {
      val moneySrv = new MoneyService[F](exchangeRatesModule.exchangeRatesService)
      val dialogEngine = new DefaultDialogEngine[F, ConnectionIO](userModule.userRepository)
      new BotModule[F, ConnectionIO](
        moneyService = moneySrv,
        dialogEngine = dialogEngine,
        startController = new StartController[F](dialogEngine),
        cbDataService = new CbDataService[F]
      )
    }
}
