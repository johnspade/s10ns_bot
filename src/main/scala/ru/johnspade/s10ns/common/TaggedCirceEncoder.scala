package ru.johnspade.s10ns.common

import io.circe.Encoder
import supertagged.TaggedType

trait TaggedCirceEncoder[T] extends TaggedType[T] {
  implicit def taggedEncoder(implicit encoder: Encoder[T]): Encoder[Type] = supertagged.lifterF[Encoder].lift
}
