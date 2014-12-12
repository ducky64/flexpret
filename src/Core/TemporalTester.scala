package FlexpretTests
import Chisel._

// TODO: should isTrace be passed?
class TemporalTester[+T <: Module](c: T, val frequency:Int)
                                   extends Tester(c, false) {
  var cycle:Int = 0
  override def step(n: Int) {
    cycle = cycle + n
    super.step(n)
  }
}

