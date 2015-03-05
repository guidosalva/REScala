package rescala.test.events


import org.junit.After
import org.junit.Before
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import rescala.events._
import rescala._




class map_EventTest extends AssertionsForJUnit with MockitoSugar {


  @Before def initialize() {
    TS.reset()
  }
  @After def cleanup() {
    TS.reset()
  }

  @Test def handlerOf_map_IsExecuted() = {
    var test = 0
    val e1 = new ImperativeEvent[Int]()
    val e1_map = e1 map ((x: Int) => (x * 2))
    e1_map += ((x: Int) => { test += 1 })

    e1(10)
    e1(10)
    assert(test == 2)
  }

  @Test def theFunctionPassedTo_map_isApplied() = {
    var test = 0
    val e1 = new ImperativeEvent[Int]()
    val e1_map = e1 map ((x: Int) => (x * 2))
    e1_map += ((x: Int)=>{ test = x })

    e1(10)
    e1(10)
    assert(test == 20)
  }

}
