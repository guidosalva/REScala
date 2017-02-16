package benchmarks.STMBank

import java.util.concurrent.locks.{Lock, ReentrantLock}
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import benchmarks.EngineParam
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{BenchmarkParams, Blackhole}
import rescala.engine.Engine
import rescala.graph.Struct
import rescala.propagation.Turn
import rescala.reactives._

import scala.concurrent.stm.{Ref, atomic}


@State(Scope.Benchmark)
class ReactiveState[S <: Struct] {


  @Param(Array("64"))
  var numberOfAccounts: Int = _

  @Param(Array("4"))
  var readWindowCount: Int = _

  @Param(Array("0.1"))
  var globalReadChance: Double = _
  var modifiedReadChance: Double = _

  var engine: Engine[S, Turn[S]] = _
  var accounts: Array[Var[Int, S]] = _
  var windows: Array[Array[Var[Int, S]]] =_


  var locks: Array[Lock] = null
  var lockWindows: Array[Array[Lock]] = null

  @Setup(Level.Iteration)
  def setup(params: BenchmarkParams, engine: EngineParam[S]) = {
    this.engine = engine.engine
    val threads = params.getThreads
    implicit val e = this.engine

    modifiedReadChance = globalReadChance / threads

    accounts = Array.fill(numberOfAccounts)(Var(0))
    windows = accounts.grouped(numberOfAccounts / readWindowCount).toArray
    assert(windows.length == readWindowCount)
    if (engine.engineName == "unmanaged") {
      locks = Array.fill(numberOfAccounts)(new ReentrantLock())
      lockWindows = locks.grouped(numberOfAccounts / readWindowCount).toArray
      assert(lockWindows.length == readWindowCount)
    }
  }
}


@State(Scope.Benchmark)
class STMState {


  @Param(Array("64"))
  var numberOfAccounts: Int = _

  @Param(Array("4"))
  var readWindowCount: Int = _

  @Param(Array("0.1"))
  var globalReadChance: Double = _
  var modifiedReadChance: Double = _

  var accounts: Array[Ref[Int]] = _
  var windows: Array[Array[Ref[Int]]] =_

  @Setup(Level.Iteration)
  def setup(params: BenchmarkParams) = {
    val threads = params.getThreads
    modifiedReadChance = globalReadChance / threads
    accounts = Array.fill(numberOfAccounts)(Ref(0))
    windows = accounts.grouped(numberOfAccounts / readWindowCount).toArray
    assert(windows.length == readWindowCount)
  }
}


@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
class BankAccounts[S <: Struct] {

  @Benchmark
  def reactive(rs: ReactiveState[S], bh: Blackhole) = {
    if (rs.locks == null) {
      val tlr = ThreadLocalRandom.current()
      if (tlr.nextDouble() < rs.modifiedReadChance) {
        val window = rs.windows(tlr.nextInt(rs.windows.length))
        rs.engine.plan(window: _*) { t =>
          val sum = window.foldLeft(0)((acc, v) => acc + v.now(t))
          bh.consume(sum)
          assert(rs.readWindowCount != 1 || sum == 0, "with a single window, the sum should be 0")
        }
      }
      else {
        val a1 = tlr.nextInt(rs.numberOfAccounts)
        val a2 = tlr.nextInt(rs.numberOfAccounts)
        if (a1 != a2) {
          val account1 = rs.accounts(a1)
          val account2 = rs.accounts(a2)
          rs.engine.plan(account1, account2) { t =>
            account1.admit(account1.now(t) + 4817)(t)
            account2.admit(account2.now(t) - 4817)(t)
          }
        }
      }
    }
    else {
      val tlr = ThreadLocalRandom.current()
      if (tlr.nextDouble() < rs.modifiedReadChance) {
        val selectedWindow = tlr.nextInt(rs.windows.length)
        val window = rs.windows(selectedWindow)
        val lockWindow = rs.lockWindows(selectedWindow)

        lockWindow.foreach(_.lock())
        try {
          rs.engine.plan(window: _*) { t =>
            val sum = window.foldLeft(0)((acc, v) => acc + v.now(t))
            bh.consume(sum)
            assert(rs.readWindowCount != 1 || sum == 0, "with a single window, the sum should be 0")
          }
        }
        finally { lockWindow.foreach(_.unlock()) }
      }
      else {
        val a1 = tlr.nextInt(rs.numberOfAccounts)
        val a2 = tlr.nextInt(rs.numberOfAccounts)
        if (a1 != a2) {
          val first = Math.min(a1, a2)
          val second = Math.max(a1, a2)
          rs.locks(first).lock()
          rs.locks(second).lock()
          try {
            val account1 = rs.accounts(a1)
            val account2 = rs.accounts(a2)
            rs.engine.plan(account1, account2) { t =>
              account1.admit(account1.now(t) + 4817)(t)
              account2.admit(account2.now(t) - 4817)(t)
            }
          } finally {
            rs.locks(first).unlock()
            rs.locks(second).unlock()
          }
        }
      }
    }
  }


  @Benchmark
  def stm(rs: STMState, bh: Blackhole) = {
    val tlr = ThreadLocalRandom.current()
    if (tlr.nextDouble() < rs.modifiedReadChance) {
      val window = rs.windows(tlr.nextInt(rs.windows.length))
      atomic { t =>
        val sum = window.foldLeft(0)((acc, v) => acc + v.get(t))
        bh.consume(sum)
        assert(rs.readWindowCount != 1 || sum == 0, "with a single window, the sum should be 0")
      }
    }
    else {
      val a1 = tlr.nextInt(rs.numberOfAccounts)
      val a2 = tlr.nextInt(rs.numberOfAccounts)
      if (a1 != a2) {
        val account1 = rs.accounts(a1)
        val account2 = rs.accounts(a2)
        atomic { t =>
          account1.set(account1.get(t) + 4817)(t)
          account2.set(account2.get(t) - 4817)(t)
        }
      }
    }
  }


}
