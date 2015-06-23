package rescala.turns

import rescala.graph.State.SimpleState
import rescala.graph.{Reactive, State}
import rescala.propagation.PropagationImpl
import rescala.synchronization.{FactoryReference, NoLocking}

import scala.util.DynamicVariable

object Engines {

  implicit val synchron: Engine[SimpleState, NoLocking[SimpleState]] = new SImpl[NoLocking[SimpleState]](new FactoryReference(State.simple) with NoLocking[SimpleState]) {
    override def plan[R](i: Reactive[SimpleState]*)(f: NoLocking[SimpleState] => R): R = synchronized(super.plan(i: _*)(f))
  }
  implicit val unmanaged: Engine[SimpleState, NoLocking[SimpleState]] = new SImpl[NoLocking[SimpleState]](new FactoryReference(State.simple) with NoLocking[SimpleState])

  implicit val default: Engine[SimpleState, NoLocking[SimpleState]] = synchron

  class SImpl[TImpl <: PropagationImpl[SimpleState]](makeTurn: => TImpl) extends Impl[SimpleState, TImpl](makeTurn) {
    /** used for the creation of state inside reactives */
    override private[rescala] def bufferFactory: SimpleState = State.simple
  }

  abstract class Impl[S <: State, TImpl <: PropagationImpl[S]](makeTurn: => TImpl) extends Engine[S, TImpl] {

    val currentTurn: DynamicVariable[Option[TImpl]] = new DynamicVariable[Option[TImpl]](None)

    override def subplan[T](initialWrites: Reactive[S]*)(f: TImpl => T): T = currentTurn.value match {
      case None => plan(initialWrites: _*)(f)
      case Some(turn) => f(turn)
    }

    /** goes through the whole turn lifecycle
      * - create a new turn and put it on the stack
      * - run the lock phase
      *   - the turn knows which reactives will be affected and can do something before anything is really done
      * - run the admission phase
      *   - executes the user defined admission code
      * - run the propagation phase
      *   - calculate the actual new value of the reactive graph
      * - run the commit phase
      *   - do cleanups on the reactives, make values permanent and so on, the turn is still valid during this phase
      * - run the observer phase
      *   - run registered observers, the turn is no longer valid but the locks are still held.
      * - run the release phase
      *   - this must always run, even in the case that something above fails. it should do cleanup and free any locks to avoid starvation.
      * - run the party! phase
      *   - not yet implemented
      * */
    override def plan[Res](initialWrites: Reactive[S]*)(admissionPhase: TImpl => Res): Res = {
 
      val turn = makeTurn
      try {
        val turnResult = currentTurn.withValue(Some(turn)) {
          turn.lockPhase(initialWrites.toList)
          val admissionResult = admissionPhase(turn)
          turn.propagationPhase()
          turn.commitPhase()
          admissionResult
        }
        turn.observerPhase()
        turnResult
      }
      catch {
        case e: Throwable =>
          turn.rollbackPhase()
          throw e
      }
      finally {
        turn.releasePhase()
      }
    }

  }

}