package example

import rescala._
import retier.communicator.tcp._
import retier.registry.{Binding, Registry}
import retier.serializer.upickle._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

import rescala.fullmv.transmitter.SignalTransmittable._
import rescala.fullmv.transmitter.EventTransmittable._


object Bindings1 {
  val eventBinding = Binding [Evt[Int]]("listEvt")
  val variableBinding1 = Binding[Signal[List[Int]]]("variable")
  val addBinding = Binding [Event[Int] => Unit]("listAdd")
}
//This method represents the server
object Server extends App {


  def add(x: Int, list:Var[List[Int]]) = { list()= list.now :+ x}
  val eventList :   Var[List[Event[Int]]]= Var(List())
  def eventAdd(x: Event[Int]) = {eventList() = eventList.now :+ x}
  val testList1 = Var (List(1,2,3))


  def eventToIntList(eList: List[Event[Int]]) = {
    if(!(eventList.now == Nil)) {
      eList.last.map {
        add(_, testList1)
      }
    }

  }
  eventList observe eventToIntList

  testList1 observe println






  val registry = new Registry
  registry.listen(TCP(1099))

  registry.bind(Bindings1.variableBinding1)(testList1)
  registry.bind(Bindings1.addBinding)(eventAdd)



   while (System.in.available() == 0) {
     Thread.sleep(1000)
   }
  registry.terminate()
}
// This method represents a client. Multiple clients may be active at a time.
object Client extends App {
  val registry = new Registry
  val remote = Await result (registry.request(TCP("localhost", 1099)), Duration.Inf)

  import scala.concurrent.ExecutionContext.Implicits._


  val listOnServer: Signal[List[Int]] = Await result (registry.lookup(Bindings1.variableBinding1, remote), Duration.Inf)
  listOnServer observe println

  var input =""
  var continueProgram = true

  //val e0: Future[rescala.Evt[Int]] = registry.lookup(Bindings1.eventBinding, remote)

  val eventAdd: Event[Int] =>  Future[Unit] =  registry.lookup(Bindings1.addBinding, remote)
  val e1 = Evt[Int]
  eventAdd(e1)

  while (continueProgram) {
    println("enter \"add\" to add a vaule or \"end\" to end")
    input = scala.io.StdIn.readLine()
    if (input == "end"){
      continueProgram = false
    }else if (input == "add"){
      println("enter a value")
      input = scala.io.StdIn.readLine()
      try
      {
        e1(input.toInt)
      }
      catch
      {
        case e:Exception => println("please enter a valid number")
      }
    }
    Thread.sleep(10)
  }


  registry.terminate()
}