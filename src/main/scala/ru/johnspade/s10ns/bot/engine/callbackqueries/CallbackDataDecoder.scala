package ru.johnspade.s10ns.bot.engine.callbackqueries

trait CallbackDataDecoder[F[_], T] {
  def decode(queryData: String): DecodeResult[F, T]
}
