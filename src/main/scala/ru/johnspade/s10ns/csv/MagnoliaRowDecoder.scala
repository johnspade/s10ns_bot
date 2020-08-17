package ru.johnspade.s10ns.csv

import kantan.csv.{DecodeError, RowDecoder}
import magnolia._

import scala.language.experimental.macros

object MagnoliaRowDecoder {
  type Typeclass[T] = RowDecoder[T]

  def combine[T](ctx: CaseClass[Typeclass, T]): Typeclass[T] =
    (e: Seq[String]) =>
      ctx.constructEither { p =>
        p.typeclass.decode(Seq(e(p.index)))
      }
        .left
        .map(_.head)

  def dispatch[T](ctx: SealedTrait[Typeclass, T]): Typeclass[T] =
    (e: Seq[String]) =>
      if (e.isEmpty)
        Left(DecodeError.OutOfBounds(0))
      else {
        ctx.subtypes
          .find(_.typeName.short == e.head)
          .map(_.typeclass.decode(e.tail))
          .getOrElse(Left(DecodeError.TypeError(s"Invalid type tag: ${e.head}")))
      }

  implicit def deriveRowDecoder[T]: Typeclass[T] = macro Magnolia.gen[T]
}
