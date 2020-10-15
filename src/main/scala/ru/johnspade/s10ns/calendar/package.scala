package ru.johnspade.s10ns

package object calendar {
  implicit class RangeHasShift(val range: Range) extends AnyVal {
    def shift(n: Int): Range = {
      val shiftedStart = range.start + n
      val shiftedEnd = range.end + n

      if ((n > 0 && (shiftedStart < range.start || shiftedEnd < range.end)) ||
        (n < 0 && (shiftedStart > range.start || shiftedEnd > range.end)))
        throw new IllegalArgumentException(s"$range.shift($n) causes number overflow")

      if (range.isInclusive)
        Range.inclusive(shiftedStart, shiftedEnd, range.step)
      else
        Range(shiftedStart, shiftedEnd, range.step)
    }

    def nextRange: Range = shift(range.size)
    def previousRange: Range = shift(-range.size)
  }
}
