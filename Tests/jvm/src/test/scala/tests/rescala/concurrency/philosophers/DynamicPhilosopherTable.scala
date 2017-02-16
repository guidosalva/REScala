package tests.rescala.concurrency.philosophers

import rescala.engine.Engine
import rescala.graph.Globals.named
import rescala.graph.Struct
import rescala.propagation.Turn
import tests.rescala.concurrency.philosophers.PhilosopherTable._

class DynamicPhilosopherTable[S <: Struct](philosopherCount: Int, work: Long)(override implicit val engine: Engine[S, Turn[S]]) extends PhilosopherTable(philosopherCount, work)(engine) {

  import engine.{Var, Signal}

  override def createTable(tableSize: Int): Seq[Seating[S]] = {
    def mod(n: Int): Int = (n + tableSize) % tableSize

    val phils = for (i <- 0 until tableSize) yield named(s"Phil($i)")(Var[Philosopher](Thinking))

    val forks = for (i <- 0 until tableSize) yield {
      val nextCircularIndex = mod(i + 1)
      named(s"Fork($i, $nextCircularIndex)")(Signal {
        phils(i)() match {
          case Hungry => Taken(i.toString)
          case Thinking =>
            phils(nextCircularIndex)() match {
              case Hungry => Taken(nextCircularIndex.toString)
              case Thinking => Free
            }
        }
      })

    }

    for (i <- 0 until tableSize) yield {
      val ownName = i.toString
      val fork1 = forks(i)
      val fork2 = forks(mod(i - 1))
      val vision = named(s"Vision($i, ${mod(i - 1)})")(Signal {
        fork1() match {
          case Taken(name) if name != ownName => WaitingFor(name)
          case Taken(`ownName`) => Eating
          case Free => fork2() match {
            case Free => Ready
            case Taken(name) => WaitingFor(name)
          }
        }
      })
      Seating(i, phils(i), fork1, forks(mod(i - 1)), vision)
    }
  }

}
