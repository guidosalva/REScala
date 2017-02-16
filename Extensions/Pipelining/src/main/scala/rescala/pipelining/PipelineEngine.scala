package rescala.pipelining

import rescala.engine.PlanImpl

/**
 * @author moritzlichter
 */

class PipelineEngine extends PlanImpl[PipelineStruct.type, PipeliningTurn] {

  /** used for the creation of state inside reactives */
  override final protected[rescala] def makeTurn(priorTurn: Option[PTurn] = None): PipeliningTurn = new PipeliningTurn(this)


  protected[pipelining] val stableTurn = makeTurn(None)

  private type PTurn = PipeliningTurn

  import Pipeline._

  @volatile
  private var turnOrder = List.empty[PTurn]
  private object turnOrderLock
  private[pipelining] def reset(): Unit = turnOrderLock.synchronized(turnOrder = Nil)

  protected[pipelining] def addTurn(turn: PTurn) = turnOrderLock.synchronized {
      val turnOrderWasEmpty = turnOrder.isEmpty
      turnOrder :+= turn
      if (turnOrderWasEmpty) {
        // If the first turn is added, it may start its deframing phase immediately after propagation
        turn.needContinue()
      }
  }
  protected[pipelining] def getTurnOrder() = turnOrder

  protected[pipelining] def isActive(turn: PTurn) = turnOrder.contains(turn)

  protected[pipelining] def canTurnBeRemoved(turn: PTurn) = turnOrderLock.synchronized(turnOrder(0) == turn)


  /**
   * Implements a depth first search of the waiting graph to check
   * whether waits waits on on
   */
  protected[pipelining] def waitsOn(waits: PTurn, on: PTurn): Boolean = {
    // Dont need synchronization here, this makes every thing faster
    // new turns are appended to the turn order, but they are appended, before any waitsOn call involving it is creates
    // thus, if on is not contained in turnOrder, it has been removed because it was finished, thus was before waits
    // Not both indices can be null, at least one turn is active, otherwise no call was made
    val currentOrder = turnOrder
    assert(currentOrder.contains(waits))
    val waitsIndex = currentOrder.indexOf(waits)
    val onIndex = currentOrder.indexOf(on)
    if (onIndex == -1) {
      true
    } else if (waitsIndex == -1) {
      assert(false)
      false
    } else
      waitsIndex > onIndex
  }

  /**
   * Calles by the PipeliningTurn if it completed its deframing phase
   */
  protected[pipelining] def turnCompleted(completedTurn: PTurn): Unit = {

    turnOrderLock.synchronized {
      assert(turnOrder.head == completedTurn)
      // Remove it from the order
      turnOrder = turnOrder.tail
      if (!turnOrder.isEmpty) {
        // And tell the new head that it is allowed to start its deframing phase
        turnOrder.head.needContinue()
      }
    }

  }

  private def bufferBlocking[A](default: A, commitStrategy: (A, A) => A, at: Reactive): BlockingPipelineBuffer[A] = {
    assert(at != null)
    assert(pipelineFor(at) != null)
    pipelineFor(at).createBlockingBuffer(default, commitStrategy)
  }

  private def bufferNonblocking[A](default: A, commitStrategy: (A, A) => A, at: Reactive): NonblockingPipelineBuffer[A] = {
    assert(at != null)
    assert(pipelineFor(at) != null)
    pipelineFor(at).createNonblockingBuffer(default, commitStrategy)
  }

}
