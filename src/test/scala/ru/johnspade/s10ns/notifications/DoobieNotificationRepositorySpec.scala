package ru.johnspade.s10ns.notifications

import java.util.UUID

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql._

class DoobieNotificationRepositorySpec extends DoobieRepositorySpec {
  private val sampleUuid         = UUID.randomUUID()
  private val sampleNotification = Notification(sampleUuid, 0L)

  test("create") {
    check(create(sampleNotification))
  }

  test("createMany") {
    check(createMany)
  }

  test("retrieveForSending") {
    check(retrieveForSending())
  }

  test("delete") {
    check(delete(sampleUuid))
  }
}
