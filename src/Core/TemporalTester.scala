package FlexpretTests

import Chisel._

class TemporalTesterTimer[+T <: Module](val tester: TemporalTester[T]) {
  var cycle: BigInt = 0
  
  def setCycle(newCycle: BigInt) {
    cycle = newCycle
  }
  
  /**
   * Expect that all nodes in the map are equal to their values.
   * @param[in] desc a string description to be printed out with errors.
   */
  def expectMapEquals(nodeValueMap: Map[Bits, BigInt], desc: String = "") {
    for ((node, value) <- nodeValueMap) {
      tester.expect(tester.peek(node) == value,
                    s"expectMapEquals: $desc: ${node.name} not equal to $value")
    }
  }
  
  /**
   * Expect that a signal will be equal to something in the specified interval,
   * offset from the Timer's current cycle (which MAY be different from the
   * Tester's cycle!). When this function returns, it will be on the cycle
   * where the signal first changed (or fail, if the expected transition did not
   * happen.
   * @param[in] deltaCycleMin: start of the interval where the signal should be
   * equal, it is an error for the signal to be equal before this.
   * @param[in] deltaCycleAdvance: "center" of the interval where the signal
   * should be equal to the value. The timer is advanced by this much on 
   * success.
   * @param[in] deltaCycleMax: end of the interval where the signal should be
   * equal, it is an error if the signal is not equal by this cycle.
   * @param[in] desc a string description to be printed out with errors.
   * @param[in] betweenConstants: a map of nodes to their expect values before
   * the signal is equal.
   */
  def expectEqualsAtAndAdvance(data: Bits, x: BigInt, deltaCycleMin: BigInt,
                               deltaCycleAdvance: BigInt, deltaCycleMax: BigInt,
                               desc: String = "",
                               betweenConstants: Map[Bits, BigInt] = Map()) {
    val targetCycleMin = cycle + deltaCycleMin
    val targetCycleMax = cycle + deltaCycleMax
    var currentCycle = tester.cycle
    val msgHeader = s"expectEqualsAtAndAdvance: $desc"
    
    assert(deltaCycleMin <= deltaCycleAdvance
           && deltaCycleAdvance <= deltaCycleMax)
    assert(targetCycleMin >= currentCycle,
           s"Interval start $targetCycleMin < current $currentCycle")
    assert(targetCycleMax >= currentCycle,
           s"Interval end $targetCycleMax < current $currentCycle")
    
    while (currentCycle < targetCycleMin) {
      tester.expect(tester.peek(data) != x, 
                    s"$msgHeader: ${data.name} equals $x before interval start")
      expectMapEquals(betweenConstants, msgHeader)
      tester.step(1)
      currentCycle += 1
    }
    
    while (currentCycle <= targetCycleMax) {
      if (tester.peek(data) == x) {
        cycle += deltaCycleAdvance
        return
      } else {
        expectMapEquals(betweenConstants, msgHeader)
      }
      tester.step(1)
      currentCycle += 1
    }
    
    tester.expect(false, s"$msgHeader: ${data.name} did not equal $x during interval")
    assert(false, s"$msgHeader: ${data.name} did not equal $x during interval")
  }
  
  /**
   * A wrapper around expectEqualsAtAndAdvance, calculating the interval in 
   * terms of a center time +/- allowable jitter.
   */
  def expectEqualsAtCentered(data: Bits, x: BigInt,
                             deltaCycleCenter: BigInt, allowableJitter: BigInt,
                             desc: String = "",
                             betweenConstants: Map[Bits, BigInt] = Map()) {
    assert(allowableJitter <= deltaCycleCenter)
    
    expectEqualsAtAndAdvance(data, x, deltaCycleCenter - allowableJitter,
                             deltaCycleCenter,
                             deltaCycleCenter + allowableJitter, 
                             desc, betweenConstants)
  }
}

class TemporalTester[+T <: Module](c: T, val frequency:Int,
                                   val max_cycle: BigInt = 0,
                                   isTrace: Boolean = false)
                                   extends Tester(c, isTrace) {
  var cycle: BigInt = 0
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
  
  def createTimerAtNow(): TemporalTesterTimer[T] = {
    val timer = new TemporalTesterTimer(this)
    timer.setCycle(cycle)
    timer
  }
}
