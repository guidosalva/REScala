package rescala.reactives

import java.util.concurrent.ConcurrentHashMap

import rescala.engine.{Engine, TurnSource}
import rescala.graph._
import rescala.propagation.{Committable, Turn}
import rescala.reactives.RExceptions.UnhandledFailureException

import scala.util.{Failure, Success, Try}

/**
  * Generic interface for observers that represent a function registered to trigger for every reevaluation of a reactive value.
  * Currently this interface is only used to allow a removal of registered observer functions.
  *
  * @tparam S Struct type used for the propagation of the signal
  */
trait Observe[S <: Struct] {
  def remove()(implicit fac: Engine[S, Turn[S]]): Unit
}

object Observe {

  private val strongObserveReferences = new ConcurrentHashMap[Observe[_], Boolean]()

  private class Obs[T, S <: Struct](bud: S#StructType[T, Reactive[S]], dependency: Pulsing[T, S], fun: Try[T] => Unit) extends Base[T, S](bud) with Reactive[S] with Observe[S] with Disconnectable[S] {
    override protected[rescala] def computeReevaluationResult()(implicit turn: Turn[S]): ReevaluationResult[S] = {
      dependency.pulse(turn).toOptionTry.foreach(t => turn.schedule(once(this, t, fun)))
      ReevaluationResult.Static(changed = false)
    }
    override def remove()(implicit fac: Engine[S, Turn[S]]): Unit = {
      disconnect()
      strongObserveReferences.remove(this: Observe[_])
    }
  }

  def weak[T, S <: Struct](dependency: Pulsing[T, S])(fun: Try[T] => Unit)(implicit maybe: TurnSource[S]): Observe[S] = {
    val incoming = Set[Reactive[S]](dependency)
    maybe(initTurn => initTurn.create(incoming) {
      val obs = new Obs(initTurn.makeStructState[T, Reactive[S]](initialIncoming = incoming, transient = false), dependency, fun)
      dependency.pulse(initTurn).toOptionTry.foreach(t => initTurn.schedule(once(this, t, fun)))
      obs
    })
  }

  def strong[T, S <: Struct](dependency: Pulsing[T, S])(fun: Try[T] => Unit)(implicit maybe: TurnSource[S]): Observe[S] = {
    val obs = weak(dependency)(fun)
    strongObserveReferences.put(obs, true)
    obs
  }


  def once[V](self: AnyRef, value: Try[V], f: Try[V] => Unit): Committable = new Committable {
    override def release(implicit turn: Turn[_]): Unit = ()
    override def commit(implicit turn: Turn[_]): Unit = turn.observe(f(value))
    override def equals(obj: scala.Any): Boolean = self.equals(obj)
    override def hashCode(): Int = self.hashCode()
  }
}

/**
  * Reactives that can be observed by a function outside the reactive graph
  *
  * @tparam P Value type stored by the pulse of the reactive value
  * @tparam S Struct type that defines the spore type used to manage the reactive evaluation
  */
trait Observable[+P, S <: Struct] {
  this : Pulsing[P, S] =>
  /** add an observer */
  final def observe(
    onSuccess: P => Unit,
    onFailure: Throwable => Unit = t => throw new UnhandledFailureException(this, t)
  )(implicit ticket: TurnSource[S]): Observe[S] = Observe.strong(this) {
    case Success(v) => onSuccess(v)
    case Failure(t) => onFailure(t)
  }
}
