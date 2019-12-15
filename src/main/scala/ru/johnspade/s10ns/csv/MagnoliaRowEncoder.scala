package ru.johnspade.s10ns.csv

import kantan.csv.RowEncoder
import magnolia._

import scala.language.experimental.macros

object MagnoliaRowEncoder {
  type Typeclass[T] = RowEncoder[T]

  def combine[T](ctx: CaseClass[RowEncoder, T]): RowEncoder[T] =
    (d: T) =>
      ctx.parameters.foldLeft(Seq.empty[String]) {
        (acc, p) => acc ++ p.typeclass.encode(p.dereference(d))
      }

  def dispatch[T](ctx: SealedTrait[RowEncoder, T]): RowEncoder[T] =
    (d: T) =>
      ctx.dispatch(d) { sub =>
        sub.typeName.short +: sub.typeclass.encode(sub.cast(d))
      }

  implicit def deriveRowEncoder[A]: RowEncoder[A] = macro Magnolia.gen[A]
}
