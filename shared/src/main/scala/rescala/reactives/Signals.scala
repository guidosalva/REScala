package rescala.reactives

import rescala.engines.Ticket
import rescala.graph.Pulse.{NoChange, Stable}
import rescala.graph._
import rescala.propagation.Turn

import scala.util.{Failure, Success, Try}

object Signals extends GeneratedSignalLift {

  object Impl {
    private class StaticSignal[T, S <: Struct](_bud: S#SporeP[T, Reactive[S]], expr: (Turn[S], => T) => T)
      extends Base[T, S](_bud) with Signal[T, S] with StaticReevaluation[T, S] {

      override def calculatePulse()(implicit turn: Turn[S]): Pulse[T] = {
        Pulse.tryCatch {
          val currentValue: Pulse[T] = pulses.base
          Pulse.diffPulse(expr(turn, currentValue.asInstanceOf[Stable[T]].value), currentValue)
        }
      }
    }

    private class DynamicSignal[T, S <: Struct](_bud: S#SporeP[T, Reactive[S]], expr: Turn[S] => T) extends Base[T, S](_bud) with Signal[T, S] with DynamicReevaluation[T, S] {
      def calculatePulseDependencies(implicit turn: Turn[S]): (Pulse[T], Set[Reactive[S]]) = {
        val (newValueTry, dependencies) = turn.collectDependencies {Try {expr(turn)}}
        newValueTry match {
          case Success(p) => (Pulse.diffPulse(p, pulses.base), dependencies)
          case Failure(t) => (Pulse.Exceptional(t), dependencies)
        }
      }
    }

    /** creates a signal that statically depends on the dependencies with a given initial value */
    def makeStatic[T, S <: Struct](dependencies: Set[Reactive[S]], init: => T)(expr: (Turn[S], => T) => T)(initialTurn: Turn[S]): Signal[T, S] = initialTurn.create(dependencies) {
      val bud: S#SporeP[T, Reactive[S]] = initialTurn.bud(Pulse.unchanged(init), transient = false, initialIncoming = dependencies)
      new StaticSignal(bud, expr)
    }

    /** creates a dynamic signal */
    def makeDynamic[T, S <: Struct](dependencies: Set[Reactive[S]])(expr: Turn[S] => T)(initialTurn: Turn[S]): Signal[T, S] = initialTurn.create(dependencies, dynamic = true) {
      val bud: S#SporeP[T, Reactive[S]] = initialTurn.bud(transient = false)
      new DynamicSignal[T, S](bud, expr)
    }
  }


  /** creates a new static signal depending on the dependencies, reevaluating the function */
  def static[T, S <: Struct](dependencies: Reactive[S]*)(expr: Turn[S] => T)(implicit ticket: Ticket[S]): Signal[T, S] = ticket { initialTurn =>
    // using an anonymous function instead of ignore2 causes dependencies to be captured, which we want to avoid
    def ignore2[I, C, R](f: I => R): (I, C) => R = (t, _) => f(t)
    Impl.makeStatic(dependencies.toSet[Reactive[S]], expr(initialTurn))(ignore2(expr))(initialTurn)
  }

  /** creates a signal that has dynamic dependencies (which are detected at runtime with Signal.apply(turn)) */
  def dynamic[T, S <: Struct](dependencies: Reactive[S]*)(expr: Turn[S] => T)(implicit ticket: Ticket[S]): Signal[T, S] =
  ticket(Impl.makeDynamic(dependencies.toSet[Reactive[S]])(expr)(_))

  /** creates a signal that folds the events in e */
  def fold[E, T, S <: Struct](e: Event[E, S], init: T)(f: (T, E) => T)(implicit ticket: Ticket[S]): Signal[T, S] = ticket { initialTurn =>
    Impl.makeStatic(Set[Reactive[S]](e), init) { (turn, currentValue) =>
      e.get(turn).fold(currentValue)(value => f(currentValue, value))
    }(initialTurn)
  }

}
