package rescala

import rescala.graph.Pulse.{Diff, NoChange}
import rescala.graph.{Enlock, DynamicReevaluation, Pulse, Pulsing, PulsingImpl, Reactive, StaticReevaluation}
import rescala.turns.{Ticket, Turn}
import rescala.graph.Buffer
import rescala.turns.Engine


object Events {

  
  
  /** the basic method to create static events */
  def static[T](name: String, dependencies: Reactive*)(calculate: Turn => Pulse[T])(implicit ticket: Ticket): Event[T] = ticket { initTurn =>
    val dependencySet = dependencies.toSet
    initTurn.create(dependencySet) {
      new PulsingImpl(initTurn.engine, dependencySet) with Event[T] with StaticReevaluation[T] {
        override def calculatePulse()(implicit turn: Turn): Pulse[T] = calculate(turn)
        override def toString = name
      }
    }
  }

  /** Used to model the change event of a signal. Keeps the last value */
  def change[T](signal: Signal[T])(implicit ticket: Ticket): Event[(T, T)] =
    static(s"(change $signal)", signal) { turn =>
      signal.pulse(turn) match {
        case Diff(value, Some(old)) => Pulse.change((old, value))
        case NoChange(_) | Diff(_, None) => Pulse.none
      }
    }


  /** Implements filtering event by a predicate */
  def filter[T](ev: Pulsing[T])(f: T => Boolean)(implicit ticket: Ticket): Event[T] =
    static(s"(filter $ev)", ev) { turn => ev.pulse(turn).filter(f) }


  /** Implements transformation of event parameter */
  def map[T, U](ev: Pulsing[T])(f: T => U)(implicit ticket: Ticket): Event[U] =
    static(s"(map $ev)", ev) { turn => ev.pulse(turn).map(f) }


  /** Implementation of event except */
  def except[T, U](accepted: Pulsing[T], except: Pulsing[U])(implicit ticket: Ticket): Event[T] =
    static(s"(except $accepted  $except)", accepted, except) { turn =>
      except.pulse(turn) match {
        case NoChange(_) => accepted.pulse(turn)
        case Diff(_, _) => Pulse.none
      }
    }


  /** Implementation of event disjunction */
  def or[T](ev1: Pulsing[_ <: T], ev2: Pulsing[_ <: T])(implicit ticket: Ticket): Event[T] =
    static(s"(or $ev1 $ev2)", ev1, ev2) { turn =>
      ev1.pulse(turn) match {
        case NoChange(_) => ev2.pulse(turn)
        case p@Diff(_, _) => p
      }
    }


  /** Implementation of event conjunction */
  def and[T1, T2, T](ev1: Pulsing[T1], ev2: Pulsing[T2], merge: (T1, T2) => T)(implicit ticket: Ticket): Event[T] =
    static(s"(and $ev1 $ev2)", ev1, ev2) { turn =>
      for {
        left <- ev1.pulse(turn)
        right <- ev2.pulse(turn)
      } yield { merge(left, right) }
    }


  /** A wrapped event inside a signal, that gets "flattened" to a plain event node */
  def wrapped[T](wrapper: Signal[Event[T]])(implicit ticket: Ticket): Event[T] = ticket { creationTurn =>
    creationTurn.create(Set[Reactive](wrapper, wrapper.get(creationTurn))) {
      new PulsingImpl[T](creationTurn.engine) with Event[T] with DynamicReevaluation[T] {
        override def calculatePulseDependencies(implicit turn: Turn): (Pulse[T], Set[Reactive]) = {
          val inner = wrapper.get
          turn.accessDynamic(inner)
          (inner.pulse, Set(wrapper, inner))
        }
      }
    }
  }
}
