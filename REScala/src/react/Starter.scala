package react

import react.log._

object Starter extends App {

  println("start!")

  // Enable default logging (with REScalaLogging project referenced)
  //react.ReactiveEngine.log = new MyLogging with AllLoggingEnabled

  val v1 = Var(1)
  val v2 = Var(2)
  val v3 = Var(3)

  val s = StaticSignal(List(v1,v2,v3)){  println("evaluated");
    v1.getValue +
    v2.getValue +
    v3.getValue + 1
  }

  v2() = 10

  println(s.getValue)

}
