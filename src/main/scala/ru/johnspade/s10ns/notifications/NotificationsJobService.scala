package ru.johnspade.s10ns.notifications

import telegramium.bots.client.Api

trait NotificationsJobService[F[_]] {
  def startNotificationsJob()(implicit bot: Api[F]): F[Unit]
}
