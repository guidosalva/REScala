package benchmarks.philosophers

import java.util
import java.util.concurrent.locks.{Lock, ReentrantLock}
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import benchmarks.philosophers.PhilosopherTable.{Thinking}
import benchmarks.{BusyThreads, EngineParam, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{BenchmarkParams, ThreadParams}

import rescala.parrp.Backoff

import scala.annotation.tailrec

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
class PhilosopherCompetition {

  @Benchmark
  def eat(comp: Competition, params: ThreadParams, work: Workload): Unit = {
    import comp.stableTable.Seating
    val myBlock = comp.blocks(params.getThreadIndex % comp.blocks.length)
    val bo      = new Backoff()
    while ({
      val seating: Seating = myBlock(ThreadLocalRandom.current().nextInt(myBlock.length))
      if (comp.manualLocking)
        manualLocking(comp)(seating)
      else
        tryUpdateCycle(comp)(seating)
    }) { bo.backoff() }

  }

  def tryUpdateCycle(comp: Competition)(seating: comp.stableTable.Seating): Boolean = {
    val res = comp.table.tryEat(seating.asInstanceOf)
    if (res) seating.philosopher.set(Thinking)(comp.table.engine.scheduler.asInstanceOf)
    !res
  }

  private def manualLocking(comp: Competition)(seating: comp.stableTable.Seating): Boolean = {
    val pos = Array(
      seating.placeNumber,
      (seating.placeNumber + 1) % comp.philosophers,
      (seating.placeNumber + 2) % comp.philosophers
    )
    util.Arrays.sort(pos)
    val firstLock  = comp.locks(pos(0))
    val secondLock = comp.locks(pos(1))
    val thirdLock  = comp.locks(pos(2))
    firstLock.lock()
    val res =
      try {
        secondLock.lock()
        try {
          thirdLock.lock()
          try {
            comp.table.tryEat(seating.asInstanceOf)
          } finally { thirdLock.unlock() }
        } finally { secondLock.unlock() }
      } finally { firstLock.unlock() }
    if (res) {
      firstLock.lock()
      try {
        secondLock.lock()
        try {
          thirdLock.lock()
          try {
            seating.philosopher.set(Thinking)(comp.table.engine.scheduler.asInstanceOf)
          } finally { thirdLock.unlock() }
        } finally { secondLock.unlock() }
      } finally { firstLock.unlock() }
    }
    !res
  }
}

@State(Scope.Benchmark)
class Competition extends BusyThreads {

  @Param(Array("16", "32"))
  var philosophers: Int = _

  @Param(Array("noconflict", "alternating"))
  var layout: String = _

  @Param(Array("static", "dynamic"))
  var tableType: String = _

  var table: PhilosopherTable = _

  lazy val stableTable = table
  import stableTable.Seating

  var blocks: Array[Array[Seating]] = _

  var manualLocking: Boolean = _
  var locks: Array[Lock]     = _

  @Setup
  def setup(params: BenchmarkParams, work: Workload, engineParam: EngineParam) = {
    manualLocking = engineParam.engineName == "unmanaged" && layout != "noconflict"
    if (manualLocking) {
      locks = Array.fill(philosophers)(new ReentrantLock())
    }
    table = tableType match {
      case "static"  => new PhilosopherTable(philosophers, work.work)(engineParam.engine)
      case "dynamic" => new DynamicPhilosopherTable(philosophers, work.work)(engineParam.engine)
      case "half"    => new HalfDynamicPhilosopherTable(philosophers, work.work)(engineParam.engine)
      case "other"   => new OtherHalfDynamicPhilosopherTable(philosophers, work.work)(engineParam.engine)
    }
    blocks = (layout match {
      case "alternating" => deal(table.seatings.toList, math.min(params.getThreads, philosophers))
      case "noconflict"  => deal(table.seatings.sliding(4, 4).map(_.head).toList, params.getThreads)
      case "random"      => List(table.seatings)
    }).map(_.toArray).toArray.asInstanceOf
  }

  @TearDown(Level.Iteration)
  def cleanEating(): Unit = {
    //print(s"actually eaten: ${ table.eaten.get() } measured: ")
    table.eaten.set(0)
    table.seatings.foreach(_.philosopher.set(Thinking)(table.engine.scheduler.asInstanceOf))
  }

  final def deal[A](initialDeck: List[A], numberOfHands: Int): List[List[A]] = {
    @tailrec
    def loop(deck: List[A], hands: List[List[A]]): List[List[A]] =
      deck match {
        case Nil          => hands
        case card :: rest => loop(rest, hands.tail :+ (card :: hands.head))
      }
    loop(initialDeck, List.fill(numberOfHands)(Nil))
  }

}
