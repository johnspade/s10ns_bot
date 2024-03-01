package ru.johnspade.s10ns.notifications

import java.util.UUID

trait NotificationRepository[D[_]] {
  def create(notification: Notification): D[Unit]

  def create(notifications: List[Notification]): D[Unit]

  def retrieveForSending(): D[Option[Notification]]

  def delete(id: UUID): D[Unit]

  def getAll: D[List[Notification]]
}
