package ru.johnspade.s10ns.notifications

trait PrepareNotificationsJobService[F[_]] {
  def startPrepareNotificationsJob(): F[Unit]
}
