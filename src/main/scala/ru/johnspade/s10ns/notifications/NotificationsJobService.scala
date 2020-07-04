package ru.johnspade.s10ns.notifications

import telegramium.bots.high.Api

trait NotificationsJobService[F[_]] {
  def startNotificationsJob()(implicit bot: Api[F]): F[Unit]
}
