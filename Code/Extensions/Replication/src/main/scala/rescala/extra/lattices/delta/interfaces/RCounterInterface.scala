package rescala.extra.lattices.delta.interfaces

import rescala.extra.lattices.delta.CRDTInterface.{DeltaMutator, DeltaQuery}
import rescala.extra.lattices.delta.DotStore._
import rescala.extra.lattices.delta._

object RCounterInterface {
  implicit def IntPairAsUIJDLattice: UIJDLattice[(Int, Int)] = new UIJDLattice[(Int, Int)] {
    override def leq(left: (Int, Int), right: (Int, Int)): Boolean = (left, right) match {
      case ((linc, ldec), (rinc, rdec)) =>
        linc <= rinc && ldec <= rdec
    }

    /** Decomposes a lattice state into its unique irredundant join decomposition of join-irreducible states */
    override def decompose(state: (Int, Int)): Iterable[(Int, Int)] = state match {
      case (inc, dec) => List((inc, 0), (0, dec))
    }

    /** By assumption: associative, commutative, idempotent. */
    override def merge(left: (Int, Int), right: (Int, Int)): (Int, Int) = (left, right) match {
      case ((linc, ldec), (rinc, rdec)) =>
        (linc max rinc, ldec max rdec)
    }

    override def bottom: (Int, Int) = (0, 0)
  }

  type State[C] = Causal[DotFun[(Int, Int)], C]

  trait RCounterCompanion {
    type State[C] = RCounterInterface.State[C]
    type Embedded = DotFun[(Int, Int)]
  }

  private def deltaState[C: CContext](
      df: Option[DotFun[(Int, Int)]] = None,
      cc: C
  ): State[C] = {
    val bottom = UIJDLattice[State[C]].bottom

    Causal(
      df.getOrElse(bottom.dotStore),
      cc
    )
  }

  def value[C: CContext]: DeltaQuery[State[C], Int] = {
    case Causal(df, _) =>
      df.values.foldLeft(0) {
        case (counter, (inc, dec)) => counter + inc - dec
      }
  }

  /** Without using fresh, reset wins over concurrent increments/decrements
    * When using fresh after every time deltas are shipped to other replicas, increments/decrements win over concurrent resets
    */
  def fresh[C: CContext]: DeltaMutator[State[C]] = {
    case (replicaID, Causal(_, cc)) =>
      val nextDot = CContext[C].nextDot(cc, replicaID)

      deltaState(
        df = Some(DotFun[(Int, Int)].empty + (nextDot -> ((0, 0)))),
        cc = CContext[C].one(nextDot)
      )
  }

  private def update[C: CContext](u: (Int, Int)): DeltaMutator[State[C]] = {
    case (replicaID, Causal(df, cc)) =>
      CContext[C].max(cc, replicaID) match {
        case Some(currentDot) if df.contains(currentDot) =>
          val newCounter = (df(currentDot), u) match {
            case ((linc, ldec), (rinc, rdec)) => (linc + rinc, ldec + rdec)
          }

          deltaState(
            df = Some(df + (currentDot -> newCounter)),
            cc = CContext[C].one(currentDot)
          )
        case _ =>
          val nextDot = CContext[C].nextDot(cc, replicaID)

          deltaState(
            df = Some(DotFun[(Int, Int)].empty + (nextDot -> u)),
            cc = CContext[C].one(nextDot)
          )
      }
  }

  def increment[C: CContext]: DeltaMutator[State[C]] = update((1, 0))

  def decrement[C: CContext]: DeltaMutator[State[C]] = update((0, 1))

  def reset[C: CContext]: DeltaMutator[State[C]] = {
    case (_, Causal(df, _)) =>
      deltaState(
        cc = CContext[C].fromSet(df.keySet)
      )
  }
}

/** An RCounter (Resettable Counter/Add Wins Counter) is a Delta CRDT modeling a counter.
  *
  * Calling fresh after every time that deltas are shipped to other replicas prevents subsequent increment/decrement
  * operations to be overwritten by concurrent reset operations.
  *
  * This counter was originally proposed by Baquera et al.
  * in "The problem with embedded CRDT counters and a solution", see [[https://dl.acm.org/doi/abs/10.1145/2911151.2911159?casa_token=D7n88K9dW7gAAAAA:m3WhHMFZxoCwGFk8DVoqJXBJpwJwrqKMLqtgKo_TSiwU_ErWgOZjo4UqYqDCb-bG3iJlXc_Ti7aB9w here]]
  */
abstract class RCounterInterface[C: CContext, Wrapper] extends CRDTInterface[RCounterInterface.State[C], Wrapper] {
  def value: Int = query(RCounterInterface.value)

  def fresh(): Wrapper = mutate(RCounterInterface.fresh)

  def increment(): Wrapper = mutate(RCounterInterface.increment)

  def decrement(): Wrapper = mutate(RCounterInterface.decrement)

  def reset(): Wrapper = mutate(RCounterInterface.reset)
}
