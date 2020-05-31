package ru.johnspade.s10ns.notifications

import java.util.UUID

import io.chrisdavenport.fuuid.FUUID
import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.notifications.DoobieNotificationRepository.NotificationSql._
import ru.johnspade.s10ns.subscription.tags.SubscriptionId

class DoobieNotificationRepositorySpec extends DoobieRepositorySpec {
  private val sampleFuuid = FUUID.fromUUID(UUID.randomUUID())
  private val sampleNotification = Notification(sampleFuuid, SubscriptionId(0L))

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
    check(delete(sampleFuuid))
  }
}
