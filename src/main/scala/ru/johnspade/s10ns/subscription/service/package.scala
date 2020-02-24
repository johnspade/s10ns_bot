package ru.johnspade.s10ns.subscription

import ru.johnspade.s10ns.bot.ValidatorNec.ValidationResult
import ru.johnspade.s10ns.bot.engine.ReplyMessage

package object service {
  type RepliesValidated = ValidationResult[List[ReplyMessage]]
}
