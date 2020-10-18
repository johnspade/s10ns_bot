package ru.johnspade.s10ns.bot

import cats.data.{Kleisli, OptionT}
import cats.implicits._
import cats.{Monad, ~>}
import ru.johnspade.s10ns.bot.engine.TelegramOps.TelegramUserOps
import ru.johnspade.s10ns.bot.engine.callbackqueries.{CallbackQueryContextMiddleware, CallbackQueryData, ContextCallbackQuery}
import ru.johnspade.s10ns.user.{User, UserRepository}

class UserMiddleware[F[_]: Monad, D[_]](private val userRepo: UserRepository[D])(implicit transact: D ~> F) {
  val userEnricher: CallbackQueryContextMiddleware[CbData, User, F] =
    _.compose(
      Kleisli { (cb: CallbackQueryData[CbData]) =>
        val retrieveUser = transact(userRepo.getOrCreate(cb.cb.from.toUser(cb.cb.message.map(_.chat.id))))
        OptionT.liftF(retrieveUser.map(ContextCallbackQuery(_, cb)))
      }
    )
}
