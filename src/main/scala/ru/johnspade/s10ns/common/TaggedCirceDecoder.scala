package ru.johnspade.s10ns.common

import io.circe.Decoder
import supertagged.TaggedType

trait TaggedCirceDecoder[T] extends TaggedType[T] {
  implicit def taggedDecoder(implicit decoder: Decoder[T]): Decoder[Type] = supertagged.lifterF[Decoder].lift
}
