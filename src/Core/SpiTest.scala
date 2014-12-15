package FlexpretTests
import Chisel._

import Core._
import FlexpretTests._

class SpiTest(c: CommandResponseQueueCore) extends TemporalTester(c, 50000000, 100000) {
  def bitInv(x: Int): Int = {
    if (x == 0) {
      1
    } else if (x == 1) {
      0
    } else {
      assert(false)
      0
    }
  }
  
  /**
   * Expect a SPI wavefrom from a SPI master.
   * @param[in] frequency: cycles between a clock period (two transitions).
   * @param[in] allowableJitter: allowable jitter on the clock, in cycles.
   * @param[in] numBits: bits to send.
   * @param[in] clockPolarity: CPOL (0 or 1), 0 means clock starts at zero.
   * @param[in] clockPhase: CPHA (0 or 1), 0 means data captured on first 
   * transition, 1 means data captured on second transition
   * @param[in] expectedFromHost: expected data out from host to Tester, an
   * array of 0 and 1s
   * @param[in] dataToHost: data to send from Tester to host, an array of 0 and
   * 1s
   */
  def expectSpiHost(period: BigInt, allowableJitter: BigInt, numBits: Int,
                    clkLine: Bits, mosiLine: Bits, misoLine: Bits,
                    clockPolarity: Int, clockPhase: Int,
                    expectedFromHost: Array[Int], dataToHost: Array[Int],
                    desc: String = "") {
    //assert(clkLine.width == 1)  # TODO: how to access this?
    //assert(mosiLine.width == 1)
    //assert(misoLine.width == 1)
    assert(numBits > 0)
    assert(expectedFromHost.length == numBits)
    assert(dataToHost.length == numBits)
    assert(clockPolarity == 1 || clockPolarity == 0)
    assert(clockPhase == 1 || clockPhase == 0)
    for (bit <- expectedFromHost) {
      assert(bit == 1 || bit == 0)
    }
    for (bit <- dataToHost) {
      assert(bit == 1 || bit == 0)
    }
    val msgHeader = s"expectSpiHost: $desc"
    
    // Expected current value of the clock. 0 is low and 1 is high. 0 also
    // implies the next edge is a low-to-high transition.
    var currentClock: Int = clockPolarity
    // Expected action on next clock transition: 0 is capture (read) and 1 is
    // change.
    var nextAction: Int = clockPhase
    var isFirst = true  // allow arbitrary waiting for the first transition to synchronize
    
    // Special case if first action is a read: load the data NOW.
    if (clockPhase == 0) {
      poke(misoLine, dataToHost(0))
    }
    
    val timer = createTimerAtNow()
    var bit: Int = 0
    while (bit < numBits) {
      if (isFirst) {
        println("Step to equals ... ")
        stepUntilEqual(clkLine, bitInv(currentClock))
        println("done")
        timer.setCycle(cycle)
      } else {
        println(s"Waiting for bit $bit, phase $clockPhase ... ")
        timer.expectEqualsAtCentered(clkLine, bitInv(currentClock),
                                     period/2, allowableJitter,
                                     desc="SPI clock transition",
                                     betweenConstants=Map(clkLine -> currentClock))
        println("done")
      }
      if (clockPhase == 0) { // capture
        expect(peek(mosiLine) == expectedFromHost(bit), 
               s"$msgHeader: bit $bit: MOSI ${mosiLine.name} mismatch")
        bit += 1
      } else if (clockPhase == 1) {  // change
        poke(misoLine, dataToHost(bit))
      } else {
        assert(false)
      }
      nextAction = bitInv(nextAction)
      currentClock = bitInv(currentClock)
    }
  }
  
  reset(5)  // TODO: justify the number
    
  println("Loading memory ... ")
  RiscvHelper.loadMem(this, c.core.imem.ispm, "../tests/examples/build/emulator/spi.inst.mem")
  RiscvHelper.loadMem(this, c.core.dmem.dspm, "../tests/examples/build/emulator/spi.data.mem")
  println("done")
  
  expectSpiHost(10000, 10, 8,
                c.io.gpio.in(0)(2), c.io.gpio.in(0)(0), c.io.gpio.out(0)(0),
                0, 0,
                Array(0, 1, 0, 0, 1, 0, 1, 0), Array(0, 0, 0, 0, 0, 0, 0, 0))
} 