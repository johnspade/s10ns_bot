package ru.johnspade.s10ns.subscription.repository

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.subscription.Subscription
import ru.johnspade.s10ns.subscription.SubscriptionDraft
import ru.johnspade.s10ns.subscription.repository.DoobieSubscriptionRepository.SubscriptionSql._
import ru.johnspade.s10ns.subscription.tags.SubscriptionId
import ru.johnspade.s10ns.user.tags.UserId

class DoobieSubscriptionRepositorySpec extends DoobieRepositorySpec {
  private val sampleS10nId    = SubscriptionId(0L)
  private val sampleS10nDraft = SubscriptionDraft.create(UserId(0L))
  private val sampleS10n      = Subscription.fromDraft(sampleS10nDraft, sampleS10nId)
  private val sampleUserId    = UserId(0L)

  test("create") {
    check(create(sampleS10nDraft))
  }

  test("get") {
    check(get(sampleS10nId))
  }

  test("getWithUser") {
    check(getWithUser(sampleS10nId))
  }

  test("getByUserId") {
    check(getByUserId(sampleUserId))
  }

  test("remove") {
    check(remove(sampleS10nId))
  }

  test("update") {
    check(update(sampleS10n))
  }

  test("disableNotificationsForUser") {
    check(disableNotifications(sampleUserId))
  }
}
