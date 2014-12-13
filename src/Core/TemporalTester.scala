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
    assert(false)  // TODO Implement me
  }
  
}

class TemporalTester[+T <: Module](c: T, val frequency:Int,
                                   val max_cycle: Int = 0,
                                   isTrace: Boolean = false)
                                   extends Tester(c, isTrace) {
  var cycle: Int = 0
  override def step(n: Int) {
    cycle = cycle + n
    if (cycle > max_cycle) {
      println(s"TemporalTester: cycle count limit exceeded: $cycle > $max_cycle")
      assert(false)  // TODO: better failure mode
    }
    super.step(n)
  }
  
  def stepUntilEqual(data: Bits, value: BigInt) {
    while (peek(data) != value) {
      step(1)
    }
  }
  
  def stepUntilNotEqual(data: Bits, value: BigInt) {
    while (peek(data) == value) {
      step(1)
    }
  }
}

