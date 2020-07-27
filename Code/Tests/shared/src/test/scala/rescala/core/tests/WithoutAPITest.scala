package rescala.core.tests

import rescala.core.{InitialChange, Initializer, Interp, REName}
import rescala.core.Initializer.InitValues
import tests.rescala.testtools.RETests


class WithoutAPITest extends RETests {
  multiEngined { engine =>
    import engine._


    class CustomSource[T](initState: REStructure#State[T, REStructure]) extends ReSource with Interp[T, REStructure] {
      outer =>

      override type Value = T
      override protected[rescala] def state: State = initState
      override protected[rescala] def name: REName = "I am a source name"
      override protected[rescala] var invariances: Seq[Value => Boolean] = Seq()


      override def interpret(v: Value): T = v

      def makeChange(newValue: T) = new InitialChange[TestStruct] {
        override val source = outer
        override def writeValue(b: T, v: T => Unit): Boolean = {
          if (b != newValue) {
            v(newValue)
            true
          }
          else false
        }
      }
    }


    class CustomDerivedString(initState: REStructure#State[String, REStructure], inputSource: Interp[String, REStructure]) extends Reactive with Interp[String, REStructure] {
      override type Value = String
      override protected[rescala] def state: State = initState
      override protected[rescala] def name: REName = "I am a name"
      override protected[rescala] var invariances: Seq[Value => Boolean] = Seq()

      override protected[rescala] def reevaluate(input: ReIn): Rout = {
        val sourceVal = input.dependStatic(inputSource)
        input.withValue(sourceVal + " :D")
      }

      override def interpret(v: Value): String = v

    }

    test("simple usage of core recsala without signals or events") {

      val customSource: CustomSource[String] = implicitly[CreationTicket]
        .createSource(new InitValues("Hi!", new Initializer.KeepValue)) { createdState =>
          new CustomSource[String](createdState)
        }

      assert("Hi!" === transaction(customSource) {_.now(customSource)})


      val customDerived: Interp[String, REStructure] = implicitly[CreationTicket]
        .create(Set(customSource), new InitValues("well this is an initial value", new Initializer.KeepValue), inite = false) { createdState =>
          new CustomDerivedString(createdState, customSource)
        }

      assert("Hi!" === transaction(customSource) {_.now(customSource)})
      assert("well this is an initial value" === transaction(customDerived) {_.now(customDerived)})

      transaction(customSource) {_.recordChange(customSource.makeChange("Hello!"))}

      assert("Hello!" === transaction(customSource) {_.now(customSource)})
      assert("Hello! :D" === transaction(customDerived) {_.now(customDerived)})

    }
  }
}
