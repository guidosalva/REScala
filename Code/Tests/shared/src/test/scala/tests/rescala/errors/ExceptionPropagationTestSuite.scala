package tests.rescala.errors

import rescala.core.Pulse
import rescala.reactives.RExceptions.ObservedException
import tests.rescala.testtools.RETests

import scala.util.{Failure, Success, Try}




class ExceptionPropagationTestSuite extends RETests { multiEngined { engine => import engine._


  // we need this because scalajs does not throw exceptions
  def div(in: Int) = div2(in, 100)
  def div2(in: Int, acc: Int) = if (in == 0) throw new ArithmeticException() else acc / in



  test("basic Signal Exceptions"){
    val v = Var(42)
    val ds = Signal { div(v()) }
    val ss = v.map(div)

    assert(ds.readValueOnce == 100 / v.readValueOnce, "dynamic arithmetic error")
    assert(ss.readValueOnce == 100 / v.readValueOnce, "static arithmetic error")

    v.set(0)

    intercept[ObservedException](ds.readValueOnce)
    intercept[ObservedException](ss.readValueOnce)

  }

  test("basic Event Exceptions"){
    val e = Evt[Int]
    val de = Event { e().map(div) }
    val se = e.map(div)

    var dres: Try[Int] = null
    var sres: Try[Int] = null

    de.map(Success(_)).recover{ case t => Some(Failure(t)) }.observe(dres = _)
    se.map(Success(_)).recover{ case t => Some(Failure(t)) }.observe(sres = _)

    e.fire(42)

    assert(dres === Success(100 / 42), "dynamic arithmetic error")
    assert(sres === Success(100 / 42), "static arithmetic error")

    e.fire(0)

    intercept[ArithmeticException](dres.get)
    intercept[ArithmeticException](sres.get)

  }


  test("more Exceptions"){
    val input = Evt[String]
    val trimmed = input.map(_.trim)
    val toInted = trimmed.map(_.toInt)
    val folded = toInted.fold(100)((acc, v) => div2(v, acc))
    val `change'd` = folded.change

    var res: rescala.reactives.Signals.Diff[Int] = null
    `change'd`.observe(res = _)


    assert(folded.readValueOnce === 100)

    input.fire("10")
    assert(folded.readValueOnce === 10, "successful fold")
    assert(res.pair === (100 -> 10), "successful changed")

    input.fire(" 2  ")
    assert(folded.readValueOnce === 5, "successful fold 2")
    assert(res.pair === (10 -> 5), "successful changed 2")

    input.fire(" 0  ")
    intercept[ObservedException](folded.readValueOnce)
    intercept[ArithmeticException](res.pair)

    input.fire(" aet ")
    intercept[ObservedException](folded.readValueOnce)
    intercept[NumberFormatException](res.pair)


    input.fire(" 2 ")
    intercept[ObservedException](folded.readValueOnce)
    intercept[NumberFormatException](res.pair)

  }

  test("signal Regenerating"){
    val input = Var("100")
    val trimmed = input.map(_.trim)
    val folded = trimmed.map(_.toInt)
    val `change'd` = folded.change

    var res: rescala.reactives.Signals.Diff[Int] = null

    `change'd`.observe(res = _)



    assert(folded.readValueOnce === 100)

    input.set("10")
    assert(folded.readValueOnce === 10, "successful fold")
    assert(res.pair === (100 -> 10), "successful changed")

    input.set(" 2  ")
    assert(folded.readValueOnce === 2, "successful fold 2")
    assert(res.pair === (10 -> 2), "successful changed2")


    input.set(" 0  ")
    assert(folded.readValueOnce === 0, "successful fold 3")
    assert(res.pair === (2 -> 0), "successful changed3")

    input.set(" aet ")
    intercept[ObservedException](folded.readValueOnce)
    intercept[NumberFormatException](res.pair)


    input.set("100")
    assert(folded.readValueOnce === 100, "successful fold 5")
    intercept[NumberFormatException](res.pair) //TODO: should maybe change?

    input.set("200")
    assert(res.pair === (100 -> 200), "successful changed3")

  }


  test("observers can abort"){
    val v = Var(0)
    val ds = Signal { div(v()) }

    var res = 100

    intercept[ObservedException]{ds.observe(res = _)}
    assert(res === 100, "can not add observers to exceptional signals")

    v.set(42)
    ds.observe(res = _)
    assert(res === 100/42, "can add observers if no longer failed")


    intercept[ObservedException]{v.set(0) }
    assert(res===100/42, "observers are not triggered on failure")
    if(engine.scheduler != rescala.extra.simpleprop.SimpleScheduler) {
      assert(v.readValueOnce === 42, "transaction is aborted on failure")
    }
  }

  test("do not observe emptiness"){
    val v = Var.empty[Int]
    val ds = Signal { div(v()) }

    var res = 100

    ds.observe(res = _)
    assert(res === 100, "adding observers to empty signal does nothing")

    v.set(42)
    assert(res === 100/42, "making signal non empty triggers observer")


    engine.transaction(v)(t => v.admitPulse(Pulse.empty)(t))
    assert(res===100/42, "observers are not triggered when empty")
    intercept[NoSuchElementException]{v.readValueOnce}
  }

  test("abort combinator"){
    if(engine.scheduler != rescala.extra.simpleprop.SimpleScheduler) {
      val v  = Var(0)
      val ds = Signal {div(v())}

      intercept[ObservedException] {ds.abortOnError("abort immediate")}

      v.set(42)
      ds.abortOnError("abort later")
      assert(ds.readValueOnce === 100 / 42, "can add observers if no longer failed")


      intercept[ObservedException] {v.set(0)}
      assert(ds.readValueOnce === 100 / 42, "observers are not triggered on failure")
      assert(v.readValueOnce === 42, "transaction is aborted on failure")
    }
  }

  test("partial recovery"){
    val v = Var(2)
    val ds = Signal { div(v()) }
    val ds2: Signal[Int] = Signal { if (ds() == 10) throw new IndexOutOfBoundsException else ds() }
    val recovered = ds2.recover{ case _: IndexOutOfBoundsException => 9000}

    assert(recovered.readValueOnce === 50)
    v.set(0)
    intercept[ObservedException](recovered.readValueOnce)
    v.set(10)
    assert(recovered.readValueOnce === 9000)
    intercept[ObservedException](ds2.readValueOnce)

  }
} }
