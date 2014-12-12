package FlexpretTests

import Chisel._

class TemporalTesterTimer[+T <: Module](val tester: TemporalTester[T]) {
  var cycle: Int = 0
  
  def setCycle(newCycle: Int) {
    cycle = newCycle
  }
  
  def expectEqualsAtAndAdvance(data: Bits, x: BigInt, deltaCycleMin: Int,
                               deltaCycleAdvance: Int, deltaCycleMax: Int,
                               betweenConstants: Map[Bits, BigInt]) {
    
  }
  
}

// TODO: should isTrace be passed?
class TemporalTester[+T <: Module](c: T, val frequency:Int, isTrace: Boolean = false)
                                   extends Tester(c, isTrace) {
  var cycle: Int = 0
  override def step(n: Int) {
    cycle = cycle + n
    super.step(n)
  }
}

