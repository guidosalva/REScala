package rescala.test





import org.junit.Before
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar


import rescala._
import makro.SignalMacro.{SignalM => Signal}
import rescala.events._



class MacroTestSuite extends AssertionsForJUnit with MockitoSugar {


  var dh: DepHolder = _
  var v:  Var[Int]  = _
  var s1: Signal[Int] = _
  var s2: Signal[Int] = _
  var s3: Signal[Int] = _



  @Before def initialize() {}



  @Test def signalReEvaluatesTheExpression() {
    v  = VarSynt(0)
    var i = 1
    var s: Signal[Int] = Signal[Int]{ v(): @unchecked; i }
    i = 2
    v.set(2)
    assert(s.get == 2)
  }

  @Test def theExpressionIsNotEvaluatedEveryTimeGetValIsCalled() {
    var a = 10
    var s: Signal[Int] = Signal[Int]{ 1 + 1 + a }
    assert(s.get === 12)
    a = 11
    assert(s.get === 12)
  }


  @Test def simpleSignalReturnsCorrectExpressions() {
    var s: Signal[Int] = Signal( 1 + 1 + 1 )
    assert(s.get === 3)
  }

  @Test def theExpressionIsEvaluatedOnlyOnce() {

    var a = 0
    val v = VarSynt(10)
    var s1: Signal[Int] = Signal{ a +=1; v() % 10 }
    var s2: Signal[Int] = Signal{ s1; a }


    assert(a == 1)
    v.set(11)
    assert(a == 2)
    v.set(21)
    assert(a == 3)
  }

  @Test def handlersAreExecuted() =  {

    var test = 0
    v = VarSynt(1)

    s1 = Signal{ 2 * v() }
    s2 = Signal{ 3 * v() }
    s3 = Signal{ s1() + s2() }

    s1 addDependent Handler{ test += 1 }
    s2 addDependent Handler{ test += 1 }
    s3 addDependent Handler{ test += 1 }

    assert(test == 0)

    v.set(3)
    assert(test == 3)

  }

  @Test def levelIsCorrectlyComputed() =  {

    var test = 0
    v = VarSynt(1)

    s1 = Signal{ 2 * v() }
    s2 = Signal{ 3 * v() }
    s3 = Signal{ s1() + s2() }

    s3.get

    assert(v.level == 0)
    assert(s1.level == 1)
    assert(s2.level == 1)
    assert(s3.level == 2)


  }


  @Test def conversionFunctionWithArgumentInSignal() =  {

    var test = 0
    val e = new ImperativeEvent[Int]()
    val s: Signal[Int] = Signal{ 2 * e.latest(0)() }

    s.change += { _ => test += 1 }
    assert(s.get == 0)
    e(2)
    assert(s.get == 4)
    e(3)
    assert(s.get == 6)
    assert(test == 2)
  }


  @Test def conversionFunctionWithoutArgumentInSignal() =  {

    var test = 0
    val e = new ImperativeEvent[Int]()
    val s: Signal[Option[Int]] = Signal{ e.latestOption() }

    s.change += { _ => test += 1 }
    assert(s.get == None)
    e(2)
    assert(s.get == Some(2))
    e(3)
    assert(s.get == Some(3))
    assert(test == 2)
  }


  @Test def conversionFunctionsWorkInSignalsInObjectConstructionInOverridenDef() =  {
    // a previous macro implementation yielded wrong results for code of the
    // following form, see:
    // https://github.com/guidosalva/examples/pull/4/files#r11724000
    var test = 0
    var e = null: ImperativeEvent[Int]
    var s = null: Signal[Int]

    abstract class A { def obj(): Unit }
    val a = new A {
      def obj() = new {
        val evt = new ImperativeEvent[Int]()
        val sig: Signal[Int] = Signal{ 2 * evt.latest(0)() }

        e = evt
        s = sig
      }
    }

    a.obj()
    s.change += { _ => test += 1 }
    assert(s.get == 0)
    e(2)
    assert(s.get == 4)
    e(3)
    assert(s.get == 6)
    assert(test == 2)
  }


  @Test def signalsNestedInVars() =  {

    val a = Var(3)
    val b = Var(Signal(a()))
    val c = Signal(b()())

    assert(c.get == 3)
    a() = 4
    assert(c.get == 4)
    b() = Signal(5)
    assert(c.get == 5)

  }


  @Test def nestedDefinedSignals() = {
    val a = Var(3)
    val b = Signal {
      val c = Signal { a() }
      c()
    }

    assert(b.get == 3)
    a() = 4
    assert(b.get == 4)
    a() = 5
    assert(b.get == 5)
  }


  @Test def useOfInsideSignal() = {
    val outside = Var(1)
    val inside = Var(10)

    def sig = Signal { outside() }

    val testsig = Signal {
      def sig = Signal { inside() }
      sig()
    }

    assert(testsig.get == 10)
    outside() = 2
    inside() = 11
    assert(testsig.get == 11)
  }


  @Test def useOfOutsideSignal() = {
    val outside = Var(1)
    val inside = Var(10)

    def sig = Signal { outside() }

    val testsig = Signal {
      {
        def sig = Signal { inside() }
        sig()
      }
      sig()
    }

    assert(testsig.get == 1)
    outside() = 2
    inside() = 11
    assert(testsig.get == 2)
  }


// Assumingly, this test fails due to https://issues.scala-lang.org/browse/SI-5465
// We should check if the test succeeds when the issue is fixed
//
//  @Test def patternMatchingAndWildcard() = {
//    val v1 = Var(List(1, 2))
//    val v2 = Var(100)
//
//    val sig = Signal {
//      v1() match {
//	      case List(_, v) => v
//	      case _ => v2()
//	    }
//    }
//
//    assert(sig.get == 2)
//    v2() = 50
//    assert(sig.get == 2)
//    v1() = List(7, 8, 9)
//    assert(sig.get == 50)
//    v2() = 4
//    assert(sig.get == 4)
//    v1() = List(10, 11)
//    assert(sig.get == 11)
//  }


  @Test def patternMatchingAnonymousFunction() = {
    val s1 = Signal { List(Some(1), Some(2), None, Some(4), None) }
    val s2 = Signal {
      s1() collect { case Some(n) => n }
    }
    assert(s2.get == List(1, 2, 4))
  }

  @Test def outerAndInnerValues() = {
    val v = Var(0)
    object obj {
      def sig = Signal { v() }
	}

    val evt = new ImperativeEvent[Int]

    val testsig = Signal {
      val localsig = obj.sig
	  val latest = evt latest -1

	  localsig() + latest()
	}

    assert(testsig.get == -1)
    evt(100)
    assert(testsig.get == 100)
    v() = 10
    assert(testsig.get == 110)
    evt(10)
    assert(testsig.get == 20)
    evt(5)
    assert(testsig.get == 15)
    v() = 50
    assert(testsig.get == 55)
  }


  @Test def chainedSignals1() = {
    import scala.language.reflectiveCalls

    val v1 = Var { 1 }
    val v2 = Var { 2 }
    val v = Var { List(new { val s = v1 }, new { val s = v2 }) }

    val sig = Signal { v() map (_.s()) }

    assert(sig.get == List(1, 2))
    v1() = 5
    assert(sig.get == List(5, 2))
    v2() = 7
    assert(sig.get == List(5, 7))
    v() = v().reverse
    assert(sig.get == List(7, 5))
  }


  @Test def chainedSignals2() = {
    import scala.language.reflectiveCalls

    val v1 = Var { 20 }
    val v2 = Var { new { def signal = Signal { v1() } } }
    val v3 = Var { new { val signal = Signal { v2() } } }

    val s = Signal { v3() }

    val sig = Signal { s().signal().signal(): @unchecked }

    assert(sig.get == 20)
    v1() = 30
    assert(sig.get == 30)
    v2() = new { def signal = Signal { 7 + v1() } }
    assert(sig.get == 37)
    v1() = 10
    assert(sig.get == 17)
    v3() = new { val signal = Signal { new { def signal = Signal { v1() } } } }
    assert(sig.get == 10)
    v2() = new { def signal = Signal { 10 + v1() } }
    assert(sig.get == 10)
    v1() = 80
    assert(sig.get == 80)
  }


  @Test def functionAsGetterForSignal() = {
    import scala.language.reflectiveCalls

    def getSignal(obj: {def signal: Signal[Int]}) = obj.signal

    val v = Var { 20 }
    val o = new { val signal = Signal { v() } }

    val sig = Signal { getSignal(o)() }

    assert(sig.get == 20)
    v() = 30
    assert(sig.get == 30)
  }


  @Test def functionAsGetterForEventAndConversionFunction() = {
    import scala.language.reflectiveCalls

    def getSignal(obj: {def evt: Event[Int]}) = obj.evt

    val e = new ImperativeEvent[Int]
    val o = new { val evt = e }

    val sig = Signal { getSignal(o).latestOption() }

    assert(sig.get == None)
    e(30)
    assert(sig.get == Some(30))
  }
}
