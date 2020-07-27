package tests.rescala.static.events

import tests.rescala.testtools.RETests


/**
 * Demonstrates some of the features and of the limitations of the
 * current implementation w.r.t. OO design, like inheritance,
 * polymorphism, overriding, etc...
 */

class OOPropertiesEventTest extends RETests { multiEngined { engine => import engine._




  test("events Are Inherited"){

    var test = 0

    class A {
      val e1 = Evt[Int]
      e1 += ((x: Int) => { test += 1 })
    }
    class B extends A {
      e1.fire(10)
    }
    new B()
    assert(test == 1)
  }


  test("can Trigger Events In Superclass"){

    var test = 0

    class A {
      val e1 = Evt[Int]
    }
    class B extends A {
      e1 += ((x: Int) => { test += 1 })
      e1.fire(10)
    }
    new B()
    assert(test == 1)
  }


  test("issue With Overriding Events"){

     intercept[Exception] {
      var test = 0

      class A {
        lazy val e1: Event[Int] = Evt[Int]
        // this will force e1 which is overridden below
        e1 += ((x: Int) => { test += 1 })
      }

      class B extends A {
        val e2 = Evt[Int]
        val e3 = Evt[Int]
        // but this override here requires e2 and e3 which are not yet initialized
        override lazy val e1: Event[Int] = e2 || e3
        e1 += ((x: Int) => { test += 1 })
        e2.fire(10)
      }
      new B()

    }
  }


  class X {}
  class Y extends X {}

  test("refine"){

    var test = 0

    class A {
      val e1: Event[X] = Evt[X]
    }
    class B extends A {
      val e2 = Evt[Y]
      val e3 = Evt[Y]
      override val e1: Event[X] = e2 || e3
      e1 += ((x: X) => { test += 1 })
      e2.fire(new Y)
    }
    new B()
    assert(test == 1)
  }

} }
