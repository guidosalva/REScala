package rescala

import rescala.graph.Pulsing
import rescala.turns.Ticket

import scala.collection.LinearSeq
import scala.collection.immutable.Queue

trait Event[+T] extends Pulsing[T] {

  /** add an observer */
  final def +=(react: T => Unit)(implicit ticket: Ticket): Observe = observe(react)(ticket)
  final def observe(react: T => Unit)(implicit ticket: Ticket): Observe = Observe(this)(react)

  /**
   * Events disjunction.
   */
  final def ||[U >: T](other: Event[U])(implicit ticket: Ticket): Event[U] = Events.or(this, other)

  /**
   * Event filtered with a predicate
   */
  final def &&(pred: T => Boolean)(implicit ticket: Ticket): Event[T] = Events.filter(this)(pred)
  final def filter(pred: T => Boolean)(implicit ticket: Ticket): Event[T] = &&(pred)

  /**
   * Event is triggered except if the other one is triggered
   */
  final def \[U](other: Event[U])(implicit ticket: Ticket): Event[T] = Events.except(this, other)

  /**
   * Events conjunction
   */
  final def and[U, R](other: Event[U], ticket: (T, U) => R)(implicit maybe: Ticket): Event[R] = Events.and(this, other, ticket)

  /**
   * Event conjunction with a merge method creating a tuple of both event parameters
   */
  final def &&[U](other: Event[U])(implicit ticket: Ticket): Event[(T, U)] = Events.and(this, other, (p1: T, p2: U) => (p1, p2))

  /**
   * Transform the event parameter
   */
  final def map[U](mapping: T => U)(implicit ticket: Ticket): Event[U] = Events.map(this)(mapping)

  /**
   * Drop the event parameter; equivalent to map((_: Any) => ())
   */
  final def dropParam(implicit ticket: Ticket): Event[Unit] = Events.map(this)(_ => ())


  /** folds events with a given fold function to create a Signal */
  final def fold[A](init: A)(fold: (A, T) => A)(implicit ticket: Ticket): Signal[A] = Signals.fold(this, init)(fold)

  /** Iterates a value on the occurrence of the event. */
  final def iterate[A](init: A)(f: A => A)(implicit ticket: Ticket): Signal[A] = fold(init)((acc, _) => f(acc))

  /**
   * Counts the occurrences of the event. Starts from 0, when the event has never been
   * fired yet. The argument of the event is simply discarded.
   */
  final def count()(implicit ticket: Ticket): Signal[Int] = fold(0)((acc, _) => acc + 1)

  /**
   * Calls f on each occurrence of event e, setting the Signal to the generated value.
   * The initial signal is obtained by f(init)
   */
  final def set[B >: T, A](init: B)(f: (B => A))(implicit ticket: Ticket): Signal[A] = fold(f(init))((_, v) => f(v))

  /** returns a signal holding the latest value of the event. */
  final def latest[S >: T](init: S)(implicit ticket: Ticket): Signal[S] = fold(init)((_, v) => v)

  /** Holds the latest value of an event as an Option, None before the first event occured */
  final def latestOption()(implicit ticket: Ticket): Signal[Option[T]] = fold(None: Option[T]) { (_, v) => Some(v) }

  /** calls factory on each occurrence of event e, resetting the Signal to a newly generated one */
  final def reset[S >: T, A](init: S)(factory: S => Signal[A])(implicit ticket: Ticket): Signal[A] = set(init)(factory).flatten()

  /**
   * Returns a signal which holds the last n events in a list. At the beginning the
   * list increases in size up to when n values are available
   */
  final def last(n: Int)(implicit ticket: Ticket): Signal[LinearSeq[T]] =
    fold(Queue[T]()) { (queue: Queue[T], v: T) =>
      if (queue.length >= n) queue.tail.enqueue(v) else queue.enqueue(v)
    }

  /** collects events resulting in a variable holding a list of all values. */
  final def list()(implicit ticket: Ticket): Signal[List[T]] = fold(List[T]())((acc, v) => v :: acc)

  /** Switch back and forth between two signals on occurrence of event e */
  final def toggle[A](a: Signal[A], b: Signal[A])(implicit ticket: Ticket): Signal[A] = ticket { implicit turn =>
    val switched: Signal[Boolean] = iterate(false) { !_ }
    Signals.dynamic(switched, a, b) { s => if (switched(s)) b(s) else a(s) }
  }

  /** Return a Signal that is updated only when e fires, and has the value of the signal s */
  final def snapshot[A](s: Signal[A])(implicit ticket: Ticket): Signal[A] = ticket { turn =>
    Signals.Impl.makeStatic(Set(this, s), s.get(turn))((t, current) => this.pulse(t).fold(current, _ => s.get(t)))(turn)
  }

  /** Switch to a new Signal once, on the occurrence of event e. */
  final def switchOnce[A](original: Signal[A], newSignal: Signal[A])(implicit ticket: Ticket): Signal[A] = ticket { implicit turn =>
    val latest = latestOption
    Signals.dynamic(latest, original, newSignal) { t =>
      latest(t) match {
        case None => original(t)
        case Some(_) => newSignal(t)
      }
    }
  }

  /**
   * Switch to a signal once, on the occurrence of event e. Initially the
   * return value is set to the original signal. When the event fires,
   * the result is a constant signal whose value is the value of the event.
   */
  final def switchTo[S >: T](original: Signal[S])(implicit ticket: Ticket): Signal[S] = {
    val latest = latestOption
    Signals.dynamic(latest, original) { s =>
      latest(s) match {
        case None => original(s)
        case Some(x) => x
      }
    }
  }

  /** Like latest, but delays the value of the resulting signal by n occurrences */
  final def delay[S >: T](init: S, n: Int)(implicit ticket: Ticket): Signal[S] = {
    val history: Signal[LinearSeq[T]] = last(n + 1)
    history.map { h => if (h.size <= n) init else h.head }
  }
}