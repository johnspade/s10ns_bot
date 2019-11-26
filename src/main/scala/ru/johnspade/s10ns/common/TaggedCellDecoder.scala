package ru.johnspade.s10ns.common

import kantan.csv.CellDecoder
import supertagged.TaggedType

trait TaggedCellDecoder[T] extends TaggedType[T] {
  implicit def taggedCellDecoder(implicit cellDecoder: CellDecoder[T]): CellDecoder[Type] = apply(cellDecoder)
}
