package rescala

import rescala.graph.Stateful
import rescala.turns.Ticket


trait Signal[+A] extends Stateful[A] {

  /** add an observer */
  final def observe(react: A => Unit)(implicit ticket: Ticket): Observe = Observe(this)(react)

  /** Return a Signal with f applied to the value */
  final def map[B](f: A => B)(implicit ticket: Ticket): Signal[B] = Signals.lift(this) { f }

  /** flatten the inner signal */
  final def flatten[B]()(implicit ev: A <:< Signal[B], ticket: Ticket) = Signals.dynamic(this) { s => this(s)(s) }

  /** Return a Signal that gets updated only when e fires, and has the value of this Signal */
  final def snapshot(e: Event[_])(implicit ticket: Ticket): Signal[A] = e.snapshot(this)

  /** Switch to (and keep) event value on occurrence of e */
  final def switchTo[U >: A](e: Event[U])(implicit ticket: Ticket): Signal[U] = e.switchTo(this)

  /** Switch to (and keep) event value on occurrence of e */
  final def switchOnce[V >: A](e: Event[_])(newSignal: Signal[V])(implicit ticket: Ticket): Signal[V] = e.switchOnce(this, newSignal)

  /** Switch back and forth between this and the other Signal on occurrence of event e */
  final def toggle[V >: A](e: Event[_])(other: Signal[V])(implicit ticket: Ticket): Signal[V] = e.toggle(this, other)

  /** Delays this signal by n occurrences */
  final def delay(n: Int)(implicit ticket: Ticket): Signal[A] = ticket { implicit turn => changed.delay(get, n) }

  /** Unwraps a Signal[Event[E]] to an Event[E] */
  final def unwrap[E](implicit evidence: A <:< Event[E], ticket: Ticket): Event[E] = Events.wrapped(this.map(evidence))

  /**
   * Create an event that fires every time the signal changes. It fires the tuple
   * (oldVal, newVal) for the signal. The first tuple is (null, newVal)
   */
  final def change(implicit ticket: Ticket): Event[(A, A)] = Events.change(this)

  /**
   * Create an event that fires every time the signal changes. The value associated
   * to the event is the new value of the signal
   */
  final def changed(implicit ticket: Ticket): Event[A] = change.map(_._2)

  /** Convenience function filtering to events which change this reactive to value */
  final def changedTo[V](value: V)(implicit ticket: Ticket): Event[Unit] = (changed && { _ == value }).dropParam

}