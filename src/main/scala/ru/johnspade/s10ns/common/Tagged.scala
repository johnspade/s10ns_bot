package ru.johnspade.s10ns.common

import supertagged.TaggedType

trait Tagged[T] extends TaggedType[T] with TaggedMeta[T] with TaggedCirceEncoder[T] with TaggedCirceDecoder[T]
