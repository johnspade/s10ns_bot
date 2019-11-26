package ru.johnspade.s10ns.common

import kantan.csv.CellEncoder
import supertagged.TaggedType

trait TaggedCellEncoder[T] extends TaggedType[T] {
  implicit def taggedCellEncoder(implicit cellEncoder: CellEncoder[T]): CellEncoder[Type] = apply(cellEncoder)
}
