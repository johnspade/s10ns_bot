package ru.johnspade.s10ns.notifications

import io.chrisdavenport.fuuid.FUUID

trait NotificationRepository[D[_]] {
  def create(notification: Notification): D[Unit]

  def create(notifications: List[Notification]): D[Unit]

  def retrieveForSending(): D[Option[Notification]]

  def delete(id: FUUID): D[Unit]
}
