package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.DoobieRepositorySpec
import ru.johnspade.s10ns.user.DoobieUserRepository.UserSql._

class DoobieUserRepositorySpec extends DoobieRepositorySpec {
  private val sampleUser = User(0L, "", None)

  test("create") {
    check(create(sampleUser))
  }

  test("get") {
    check(get(0L))
  }

  test("update") {
    check(update(sampleUser))
  }
}
