package ru.johnspade.s10ns.user

import ru.johnspade.s10ns.common.Tagged

object tags {
  object UserId extends Tagged[Long]
  type UserId = UserId.Type

  object FirstName extends Tagged[String]
  type FirstName = FirstName.Type

  object LastName extends Tagged[String]
  type LastName = LastName.Type

  object Username extends Tagged[String]
  type Username = Username.Type

  object ChatId extends Tagged[Long]
  type ChatId = ChatId.Type
}
