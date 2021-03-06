package rescala

import rescala.parrp.{Backoff, ParRP}

/** REScala has two main abstractions. [[rescala.default.Event]] and [[rescala.default.Signal]] commonly referred to as reactives.
  * Use [[rescala.default.Var]] to create signal sources and [[rescala.default.Evt]] to create event sources.
  *
  * Events and signals can be created from other reactives by using combinators,
  * signals additionally can be created using [[rescala.default.Signal]] expressions.
  */
object default extends interface.RescalaInterface with ParRP {
  override val scheduler: Scheduler = parrpWithBackoff(() => new Backoff())
}
