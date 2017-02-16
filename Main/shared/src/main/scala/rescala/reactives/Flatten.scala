package rescala.reactives

import rescala.engines.TurnSource
import rescala.graph.Struct

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.reflect.ClassTag

sealed trait Flatten[-A, S <: Struct, R] {
  def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): R
}
object Flatten {
  implicit def flattenSignal[A, S <: Struct, B](implicit ev: A <:< Signal[B, S]): Flatten[A, S, Signal[B, S]] = new Flatten[A, S, Signal[B, S]] {
    def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[B, S] = Signals.dynamic(sig) { s => s.depend(ev(s.depend(sig))) }
  }
  implicit def flattenSignalTraversable
  [A, S <: Struct, B, T[U] <: TraversableLike[U, T[U]], Sig[A1, S1 <: Struct] <: Signal[A1, S1]]
  (implicit ev: A <:< T[Sig[B, S]], cbf: CanBuildFrom[T[_], B, T[B]]): Flatten[A, S, Signal[T[B], S]] = new Flatten[A, S, Signal[T[B], S]] {
    def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[T[B], S] = Signals.dynamic(sig) { s => ev(s.depend(sig)) map { (r: Signal[B, S]) => s.depend(r)} }
  }
  implicit def flattenSignalArray
  [A, S <: Struct, B: ClassTag, Sig[U, V <: Struct] <: Signal[U, V]]
  (implicit ev: A <:< Array[Sig[B, S]]): Flatten[A, S, Signal[Array[B], S]] = new Flatten[A, S, Signal[Array[B], S]] {
    def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[Array[B], S] = Signals.dynamic(sig) { s => ev(s.depend(sig)) map { (r: Signal[B, S]) => s.depend(r)} }
  }
  implicit def flattenSignalOption
  [A, S <: Struct, B, Sig[U, V <: Struct] <: Signal[U, V]]
  (implicit ev: A <:< Option[Sig[B, S]]): Flatten[A, S, Signal[Option[B], S]] = new Flatten[A, S, Signal[Option[B], S]] {
    def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): Signal[Option[B], S] = Signals.dynamic(sig) { s => ev(s.depend(sig)) map { (r: Signal[B, S]) => s.depend(r)} }
  }
  implicit def flattenEvent[A, S <: Struct, B](implicit ev: A <:< Event[B, S]): Flatten[A, S, Event[B, S]] = new Flatten[A, S, Event[B, S]] {
    def apply(sig: Signal[A, S])(implicit ticket: TurnSource[S]): Event[B, S] = Events.dynamic(sig) { s => s.depend(s.depend(sig)) }

  }
}
