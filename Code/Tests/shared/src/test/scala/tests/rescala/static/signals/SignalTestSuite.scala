package tests.rescala.static.signals

import java.util.concurrent.atomic.AtomicInteger

import rescala.core.infiltration.Infiltrator.assertLevel
import tests.rescala.testtools.RETests


class SignalTestSuite extends RETests { multiEngined { engine => import engine._


  test("handler Is Called When Change Occurs"){

    var test = 0
    val v1 = Var(1)
    val v2 = Var(2)

    val s1 = Signals.lift(v1, v2) { _ + _ }
    s1.changed += { (_) => test += 1 }

    assert(s1.readValueOnce == 3)
    assert(test == 0)

    v2.set(3)
    assert(s1.readValueOnce == 4)
    assert(test == 1)

    v2.set(3)
    assert(s1.readValueOnce == 4)
    assert(test == 1)

  }


  test("signal Reevaluates The Expression When Something It Depends On Is Updated"){
    val v = Var(0)
    var i = 1
    val s = Signal { v() + i }
    i = 2
    assert(s.readValueOnce == 1)
    v.set(2)
    assert(s.readValueOnce == 4)
  }

  test("the Expression Is Not Evaluated Every Time now Is Called"){
    var a = 10
    val s = Signal(1 + 1 + a)
    assert(s.readValueOnce === 12)
    a = 11
    assert(s.readValueOnce === 12)
  }



  test("level Is Correctly Computed"){

    val v = Var(1)

    val s1 = Signal { 2 * v() }
    val s2 = Signal { 3 * v() }
    val s3 = Signal { s1() + s2() }

    assertLevel(v, 0)
    assertLevel(s1, 1)
    assertLevel(s2, 1)
    assertLevel(s3, 2)


  }



  test("dependant Is Only Invoked On Value Changes"){
    var changes = 0
    val v = Var(1)
    val s = Signal {
      changes += 1; v() + 1
    }
    assert(changes === 1)
    assert(s.readValueOnce === 2)
    v.set(2)
    assert(s.readValueOnce === 3)
    assert(changes === 2)
    v.set(2)
    assert(changes === 2) // is actually 3
  }







  test("creating signals in signals based on changing signals"){
    val v0 = Var("level 0")
    val v3 = v0.map(_ + "level 1").map(_  + "level 2").map(_ + "level 3")

    val `dynamic signal changing from level 1 to level 5` = Signal {
      if (v0() == "level 0") v0() else {
        v3.map(_ + "level 4 inner").apply()
      }
    }
    assert(`dynamic signal changing from level 1 to level 5`.readValueOnce == "level 0")
    //note: will start with level 5 because of static guess of current level done by the macro expansion
    assertLevel(`dynamic signal changing from level 1 to level 5`, 5)

    v0.set("level0+")
    assert(`dynamic signal changing from level 1 to level 5`.readValueOnce == "level0+level 1level 2level 3level 4 inner")
    assertLevel(`dynamic signal changing from level 1 to level 5`, 5)
  }



  test("signal Reevaluates The Expression"){
    val v = Var(0)
    var i = 1
    val s: Signal[Int] = v.map { _ => i }
    i = 2
    v.set(2)
    assert(s.readValueOnce == 2)
  }

  test("the Expression Is Note Evaluated Every Time Get Val Is Called"){
    var a = 10
    val s: Signal[Int] = Signals.static()(_ => 1 + 1 + a)
    assert(s.readValueOnce === 12)
    a = 11
    assert(s.readValueOnce === 12)
  }


  test("simple Signal Returns Correct Expressions"){
    val s: Signal[Int] = Signals.static()(_ => 1 + 1 + 1)
    assert(s.readValueOnce === 3)
  }

  test("the Expression Is Evaluated Only Once"){

    var a = 0
    val v = Var(10)
    val s1: Signal[Int] = v.map { i =>
      a += 1
      i % 10
    }


    assert(a == 1)
    v.set(11)
    assert(a == 2)
    v.set(21)
    assert(a == 3)
    assert(s1.readValueOnce === 1)
  }

  test("handlers Are Executed"){

    val test = new AtomicInteger(0)
    val v = Var(1)

    val s1 = v.map { 2 * _ }
    val s2 = v.map { 3 * _ }
    val s3 = Signals.lift(s1, s2) { _ + _ }

    s1.changed += { (_) => test.incrementAndGet() }
    s2.changed += { (_) => test.incrementAndGet() }
    s3.changed += { (_) => test.incrementAndGet() }

    assert(test.get == 0)

    v.set(3)
    assert(test.get == 3)
  }

  test("level Is Correctly Computed with combinators"){

    val v = Var(1)

    val s1 = v.map { 2 * _ }
    val s2 = v.map { 3 * _ }
    val s3 = Signals.lift(s1, s2) { _ + _ }

    assertLevel(v, 0)
    assertLevel(s1, 1)
    assertLevel(s2, 1)
    assertLevel(s3, 2)
  }


  test("no Change Propagation"){
    val v = Var(1)
    val s = v.map(_ => 1)
    val s2 = Signal { s() }

    assert(s2.readValueOnce === 1)
    assert(s.readValueOnce === 1)

    v.set(2)
    assert(s.readValueOnce === 1)
    assert(s2.readValueOnce === 1)


    v.set(2)
    assert(s2.readValueOnce === 1)
    assert(s.readValueOnce === 1)


    v.set(3)
    assert(s2.readValueOnce === 1)
    assert(s.readValueOnce === 1)


  }

  test("graph cost example") {

    def mini(x: Var[Map[Signal[Int], Int]]): Signal[Int] = Signal.dynamic {
      val (node, value) = x().minBy  { case (n, v) => n() + v }
      node() + value
    }

    val root = Signal {0}

    val parentA = Var(Map(root -> 2))
    val WeightA = mini(parentA)

    assert(WeightA.readValueOnce == 2)

    val parentB = Var(Map(root -> 1))
    val WeightB = mini(parentB)

    assert(WeightB.readValueOnce == 1)


    val parentC = Var(Map(WeightA -> 3, WeightB -> 10))
    val WeightC = mini(parentC)

    assert(WeightC.readValueOnce == 5)


    parentC.transform(_ + (WeightB -> 1))

    assert(WeightC.readValueOnce == 2)

  }

} }
