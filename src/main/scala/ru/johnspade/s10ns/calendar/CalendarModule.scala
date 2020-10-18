package ru.johnspade.s10ns.calendar

import cats.effect.Sync
import telegramium.bots.high.Api

final class CalendarModule[F[_]] private (
  val calendarService: CalendarService,
  val calendarController: CalendarController[F]
)

object CalendarModule {
  def make[F[_]: Sync](implicit bot: Api[F]): F[CalendarModule[F]] =
    Sync[F].delay {
      val calendarSrv = new CalendarService
      new CalendarModule(
        calendarService = calendarSrv,
        calendarController = new CalendarController[F](calendarSrv)
      )
    }
}
