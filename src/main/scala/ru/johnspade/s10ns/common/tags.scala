package ru.johnspade.s10ns.common

object tags {
  object PageNumber extends Tagged[Int] with TaggedCellEncoder[Int]
  type PageNumber = PageNumber.Type
}
