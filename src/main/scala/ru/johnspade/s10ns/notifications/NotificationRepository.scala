package ru.johnspade.s10ns.notifications

trait NotificationRepository[D[_]] {
  def create(notification: Notification): D[Unit]
}
