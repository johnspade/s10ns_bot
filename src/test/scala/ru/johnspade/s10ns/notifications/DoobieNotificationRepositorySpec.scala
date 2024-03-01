package ru.johnspade.s10ns.notifications

import java.util.UUID

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql._
import ru.johnspade.s10ns.subscription.tags.SubscriptionId

class DoobieNotificationRepositorySpec extends DoobieRepositorySpec {
  private val sampleUuid         = UUID.randomUUID()
  private val sampleNotification = Notification(sampleUuid, SubscriptionId(0L))

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
