package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.user.DoobieUserRepository.UserSql._
import ru.johnspade.s10ns.user.tags.FirstName
import ru.johnspade.s10ns.user.tags.UserId

class DoobieUserRepositorySpec extends DoobieRepositorySpec {
  private val sampleUser = User(UserId(0L), FirstName(""), None)

  test("create") {
    check(create(sampleUser))
  }

  test("get") {
    check(get(UserId(0L)))
  }

  test("update") {
    check(update(sampleUser))
  }
}
