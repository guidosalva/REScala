package rescala.test.concurrency


import java.util.concurrent.{TimeUnit, CountDownLatch}

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import rescala.propagation.Turn
import rescala.propagation.turns.Pessimistic
import rescala.propagation.{TurnFactory, Turn}
import rescala.signals.{Signals, Var}


class PessimisticTest extends AssertionsForJUnit {

  implicit val turnFactory: TurnFactory = Pessimistic

  def thread(f: => Unit): Thread = {
    val t = new Thread(new Runnable {
      override def run(): Unit = f
    })
    t.start()
    t
  }

  @Test def runOnIndependentParts(): Unit = {
    val v1 = Var(false)
    val v2 = Var(false)
    val latch = new CountDownLatch(2)
    val s1 = v1.map{ v => if (v) {latch.countDown(); latch.await(1, TimeUnit.SECONDS)} else false}
    val s2 = v2.map{ v => if (v) {latch.countDown(); latch.await(1, TimeUnit.SECONDS)} else false}

    val t = thread(v1.set(true))
    v2.set(true)
    t.join()

    assert(s1.get === true && s2.get === true)
    assert(latch.getCount === 0)
  }

  @Test def summedSignals(): Unit = {
    val sources = List.fill(100)(Var(0))
    val latch = new CountDownLatch(1)
    val mapped = sources.map(s => s.map(_ + 1))
    val sum = mapped.reduce(Signals.lift(_, _)(_ + _))
    val threads = sources.map(v => thread{latch.await(); v.set(1)})
    latch.countDown()
    threads.foreach(_.join())

    assert(sum.get === 200)
  }
  
  @Test def crossedDynamicDependencies(): Unit = {
    val v1 = Var(false)
    val v2 = Var(false)
    val latch = new CountDownLatch(2)
    val s11 = v1.map{ v => if (v) {latch.countDown(); latch.await(1, TimeUnit.SECONDS)} else false}
    val s12 = Signals.dynamic(s11){ t => if (s11(t)) v2(t) else false }
    val s21 = v2.map{ v => if (v) {latch.countDown(); latch.await(1, TimeUnit.SECONDS)} else false}
    val s22 = Signals.dynamic(s21){ t => if (s21(t)) v1(t) else false }
    var results = List[Boolean]()
    s12.changed.+={v => println(v); results ::= v}
    s22.changed.+={v => println(v); results ::= v}


    assert(results === Nil)

    val t = thread(v1.set(true))
    v2.set(true)
    t.join()

    assert(results === List(true))
    assert(latch.getCount === 0)
  }

}
