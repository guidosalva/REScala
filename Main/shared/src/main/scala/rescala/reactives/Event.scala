package rescala.reactives

import rescala.engine.{Engine, TurnSource}
import rescala.graph.Pulse.{Change, Exceptional, NoChange}
import rescala.graph._
import rescala.propagation.Turn
import rescala.reactives.RExceptions.EmptySignalControlThrowable

import scala.collection.immutable.{LinearSeq, Queue}
import scala.language.higherKinds

/**
  * Base signal interface for all signal implementations.
  * Please note that any event implementation should have the EV type parameter set to itself and be paired with
  * exactly one signal implementation it is compatible with by setting the SL type parameter.
  * This relationship needs to be symmetrical.
  *
  * @tparam T Type returned when the event fires
  * @tparam S Struct type used for the propagation of the event
  */
trait Event[+T, S <: Struct] extends PulseOption[T, S] with Observable[T, S] {

  def disconnect()(implicit engine: Engine[S, Turn[S]]): Unit


  /** collect results from a partial function */
  final def collect[U](pf: PartialFunction[T, U])(implicit ticket: TurnSource[S]): Event[U, S] = Events.static(s"(collect $this)", this) { turn => Pulse.fromOption(get(turn).flatMap(pf.lift)) }

  /** add an observer */
  final def +=(react: T => Unit)(implicit ticket: TurnSource[S]): Observe[S] = observe(react)(ticket)


  final def recover[R >: T](onFailure: Throwable => R)(implicit ticket: TurnSource[S]): Event[R, S] = Events.static(s"(recover $this)", this) { turn =>
    pulse(turn) match {
      case Exceptional(t) => Pulse.Change(onFailure(t))
      case other => other
    }
  }


  /** Events disjunction. */
  final def ||[U >: T](other: Event[U, S])(implicit ticket: TurnSource[S]): Event[U, S] = {
    Events.static(s"(or $this $other)", this, other) { turn =>
      val tp = this.pulse(turn)
      if (tp.isChange) tp else other.pulse(turn)
    }
  }

  /** EV filtered with a predicate */
  final def filter(pred: T => Boolean)(implicit ticket: TurnSource[S]): Event[T, S] = Events.static(s"(filter $this)", this) { turn => pulse(turn).filter(pred) }
  /** EV filtered with a predicate */
  final def &&(pred: T => Boolean)(implicit ticket: TurnSource[S]): Event[T, S] = filter(pred)

  /** EV is triggered except if the other one is triggered */
  final def \[U](except: Event[U, S])(implicit ticket: TurnSource[S]): Event[T, S] = {
    Events.static(s"(except $this  $except)", this, except) { turn =>
      except.pulse(turn) match {
        case NoChange => this.pulse(turn)
        case Change(_) => Pulse.NoChange
        case ex@Exceptional(_) => ex
      }
    }
  }

  /** Events conjunction */
  final def and[U, R](other: Event[U, S])(merger: (T, U) => R)(implicit ticket: TurnSource[S]): Event[R, S] = {
    Events.static(s"(and $this $other)", this, other) { turn =>
      for {
        left <- this.pulse(turn)
        right <- other.pulse(turn)
      } yield {merger(left, right)}
    }
  }

  /** Event conjunction with a merge method creating a tuple of both event parameters */
  final def zip[U](other: Event[U, S])(implicit ticket: TurnSource[S]): Event[(T, U), S] = and(other)(Tuple2.apply)

  /** Transform the event parameter */
  final def map[U](mapping: T => U)(implicit ticket: TurnSource[S]): Event[U, S] = Events.static(s"(map $this)", this) { turn => pulse(turn).map(mapping) }


  /** Drop the event parameter; equivalent to map((_: Any) => ()) */
  final def dropParam(implicit ticket: TurnSource[S]): Event[Unit, S] = map(_ => ())


  /** folds events with a given fold function to create a Signal */
  final def fold[A](init: A)(folder: (A, T) => A)(implicit ticket: TurnSource[S]): Signal[A, S] = {
    def f(a: => A, t: => T) = folder(a, t)
    lazyFold(init)(f)
  }

  /** folds events with a given fold function to create a Signal allowing recovery of exceptional states by ignoring the stable value */
  final def lazyFold[A](init: => A)(folder: (=> A, => T) => A)(implicit ticket: TurnSource[S]): Signal[A, S] = ticket { initialTurn =>
    Signals.Impl.makeStatic(Set[Reactive[S]](this), init) { (turn, currentValue) =>
      get(turn).fold(currentValue)(value => folder(currentValue, value))
    }(initialTurn)
  }

  /** reduces events with a given reduce function to create a Signal */
  final def reduce[A](reducer: (=> A, => T) => A)(implicit ticket: TurnSource[S]): Signal[A, S] = lazyFold(throw EmptySignalControlThrowable)(reducer)

  /** Applies a function on the current value of the signal every time the event occurs,
    * starting with the init value before the first event occurrence */
  final def iterate[A](init: A)(f: A => A)(implicit ticket: TurnSource[S]): Signal[A, S] = fold(init)((acc, _) => f(acc))

  /**
    * Counts the occurrences of the event. Starts from 0, when the event has never been
    * fired yet. The argument of the event is simply discarded.
    */
  final def count()(implicit ticket: TurnSource[S]): Signal[Int, S] = fold(0)((acc, _) => acc + 1)

  /**
    * Calls f on each occurrence of event e, setting the SL to the generated value.
    * The initial signal is obtained by f(init)
    */
  final def set[B >: T, A](init: B)(f: (B => A))(implicit ticket: TurnSource[S]): Signal[A, S] = fold(f(init))((_, v) => f(v))

  /** returns a signal holding the latest value of the event. */
  final def latest[T1 >: T](init: T1)(implicit ticket: TurnSource[S]): Signal[T1, S] = fold(init)((_, v) => v)
  final def latest()(implicit ticket: TurnSource[S]): Signal[T, S] = reduce[T]((_, v) => v)

  /** Holds the latest value of an event as an Option, None before the first event occured */
  final def latestOption()(implicit ticket: TurnSource[S]): Signal[Option[T], S] = fold(None: Option[T]) { (_, v) => Some(v) }

  /** calls factory on each occurrence of event e, resetting the SL to a newly generated one */
  final def reset[T1 >: T, A, R](init: T1)(factory: T1 => Signal[A, S])(implicit ticket: TurnSource[S], ev: Flatten[Signal[A, S], S, R]): R = set(init)(factory).flatten(ev, ticket)

  /**
    * Returns a signal which holds the last n events in a list. At the beginning the
    * list increases in size up to when n values are available
    */
  final def last(n: Int)(implicit ticket: TurnSource[S]): Signal[LinearSeq[T], S] = {
    fold(Queue[T]()) { (queue: Queue[T], v: T) =>
      if (queue.length >= n) queue.tail.enqueue(v) else queue.enqueue(v)
    }
  }

  /** collects events resulting in a variable holding a list of all values. */
  final def list()(implicit ticket: TurnSource[S]): Signal[List[T], S] = fold(List[T]())((acc, v) => v :: acc)

  /** Switch back and forth between two signals on occurrence of event e */
  final def toggle[A](a: Signal[A, S], b: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[A, S] = ticket { turn =>
    val switched: Signal[Boolean, S] = iterate(false) {!_}(turn)
    Signals.dynamic(switched, a, b) { s => if (s.depend(switched)) s.depend(b) else s.depend(a) }(turn)
  }

  /** Return a Signal that is updated only when e fires, and has the value of the signal s */
  final def snapshot[A](s: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[A, S] = ticket { turn =>
    Signals.Impl.makeStatic(Set[Reactive[S]](this, s), s.get(turn)) { (t, current) =>
      this.get(t).fold(current)(_ => s.get(t))
    }(turn)
  }


  /** Switch to a new Signal once, on the occurrence of event e. */
  final def switchOnce[A](original: Signal[A, S], newSignal: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[A, S] = ticket { turn =>
    val latest = latestOption()(turn)
    Signals.dynamic(latest, original, newSignal) { t =>
      t.depend(latest) match {
        case None => t.depend(original)
        case Some(_) => t.depend(newSignal)
      }
    }(turn)
  }

  /**
    * Initially the result signal has the value of the original signal.
    * Every time the event fires, the result signal changes to the value of the event,
    * the original signal is no longer used.
    */
  final def switchTo[T1 >: T](original: Signal[T1, S])(implicit ticket: TurnSource[S]): Signal[T1, S] = ticket { turn =>
    val latest = latestOption()(turn)
    Signals.dynamic(latest, original) { s =>
      s.depend(latest) match {
        case None => s.depend(original)
        case Some(x) => x
      }
    }(turn)
  }

  /** Like latest, but delays the value of the resulting signal by n occurrences */
  final def delay[T1 >: T](init: T1, n: Int)(implicit ticket: TurnSource[S]): Signal[T1, S] = ticket { turn =>
    val history: Signal[LinearSeq[T], S] = last(n + 1)(turn)
    history.map { h => if (h.size <= n) init else h.head }(turn)
  }

  /** returns the values produced by the last event produced by mapping this value */
  final def flatMap[B](f: T => Event[B, S])(implicit ticket: TurnSource[S]): Event[B, S] = ticket { implicit turn => map(f).latest(Evt[B, S]).flatten }

  /** promotes the latest inner event to an outer event */
  final def flatten[B](implicit ticket: TurnSource[S], ev: T <:< Event[B, S]): Event[B, S] = flatMap(ev.apply)

}
