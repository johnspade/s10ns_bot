package ru.johnspade.s10ns
package csv

import kantan.csv.{CellDecoder, CellEncoder, DecodeError, RowDecoder, RowEncoder}
import shapeless.labelled.{FieldType, field}
import shapeless.{:+:, CNil, Coproduct, Inl, Inr, LabelledGeneric, Lazy, Witness}
import supertagged._

trait CsvInstances {
  implicit def liftedCellEncoder[T, U](implicit cellEncoder: CellEncoder[T]): CellEncoder[T @@ U] =
    lifterF[CellEncoder].lift
  implicit def liftedCellDecoder[T, U](implicit cellDecoder: CellDecoder[T]): CellDecoder[T @@ U] =
    lifterF[CellDecoder].lift

  implicit val cnilEncoder: RowEncoder[CNil] = RowEncoder.from(_ => Seq.empty)

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](
    implicit
    witness: Witness.Aux[K],
    hEncoder: Lazy[RowEncoder[H]],
    tEncoder: RowEncoder[T]
  ): RowEncoder[FieldType[K, H] :+: T] =
    RowEncoder.from {
      case Inl(h) => witness.value.name +: hEncoder.value.encode(h)
      case Inr(t) => tEncoder.encode(t)
    }

  implicit def genericEncoder[A, R](
    implicit
    gen: LabelledGeneric.Aux[A, R],
    enc: Lazy[RowEncoder[R]]
  ): RowEncoder[A] =
    RowEncoder.from(a => enc.value.encode(gen.to(a)))

  implicit val cnilDecoder: RowDecoder[CNil] = RowDecoder.from { e =>
    CellDecoder[String].decode(e.head) match {
      case Right(value) => Left(DecodeError.TypeError(s"Invalid type tag: $value"))
      case Left(value) => Left(value)
    }
  }

  implicit def coproductDecoder[K <: Symbol, H, T <: Coproduct](
    implicit
    witness: Witness.Aux[K],
    hDecoder: Lazy[RowDecoder[H]],
    tDecoder: RowDecoder[T]
  ): RowDecoder[FieldType[K, H] :+: T] =
    RowDecoder.from { e =>
      if (e.head == witness.value.name) hDecoder.value.decode(e.tail).map(x => Inl(field[K](x)))
      else tDecoder.decode(e).map(Inr(_))
    }

  implicit def genericDecoder[A, R](
    implicit
    gen: LabelledGeneric.Aux[A, R],
    dec: Lazy[RowDecoder[R]]
  ): RowDecoder[A] =
    RowDecoder.from(e => dec.value.decode(e).map(gen.from))
}
