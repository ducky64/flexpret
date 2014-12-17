package FlexpretTests
import Chisel._

import Core._

import scala.util.continuations._

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
  
  // scheduleNewThread(new WaitUntilEquals(c, this, Map(c.io.commandIn.valid -> 2)))
  // scheduleNewThread(new WaitUntilEquals(c, this, Map(c.io.commandIn.valid -> 2)))
  
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
    assert(clkLine.getWidth() == 1)
    assert(mosiLine.getWidth() == 1)
    assert(misoLine.getWidth() == 1)
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
    
    /*val timer = createTimerAtNow()
    var bit: Int = 0
    while (bit < numBits) {
      if (isFirst) {
        stepUntilEqual(clkLine, bitInv(currentClock))
        timer.setCycle(cycle)
        isFirst = false
      } else {
        timer.expectEqualsAtCentered(clkLine, bitInv(currentClock),
                                     period/2, allowableJitter,
                                     desc="SPI clock transition",
                                     betweenConstants=Map(clkLine -> currentClock))
        stepUntilEqual(clkLine, bitInv(currentClock))
      }
      println(s"Cycle $cycle, clk ${peek(clkLine)}, MOSI ${peek(mosiLine)}")
      
      if (nextAction == 0) { // capture
        println(s"Got bit $bit = ${peek(mosiLine)}")
        expect(peek(mosiLine) == expectedFromHost(bit), 
               s"$msgHeader: bit $bit: MOSI ${mosiLine.name} mismatch")
        bit += 1
      } else if (nextAction == 1) {  // change
        poke(misoLine, dataToHost(bit))
      } else {
        assert(false)
      }
      nextAction = bitInv(nextAction)
      currentClock = bitInv(currentClock)
    }
    
    // Special case to handle when clockPhase=0, to verify that it does return
    // to the idle state.
    if (currentClock != clockPolarity) {
      assert(bitInv(currentClock) == clockPolarity)
      timer.expectEqualsAtCentered(clkLine, bitInv(currentClock),
                                   period/2, allowableJitter,
                                   desc="SPI clock transition",
                                   betweenConstants=Map(clkLine -> currentClock))
      println(s"Cycle $cycle, clk ${peek(clkLine)}, MOSI ${peek(mosiLine)}")
    }*/
  }
  
  /*
   * Sends a command to the core's peripheral bus. Returns once the command
   * queue is empty (i.e. the command has been read).
   */
  def sendCommand(command: BigInt, desc: String = "") {
    val msgHeader = s"sendCommand ($command): $desc"
    //stepUntilEqual(c.io.commandIn.ready, 1)
    poke(c.io.commandIn.bits, command)
    poke(c.io.commandIn.valid, 1)
    step(1)
    poke(c.io.commandIn.valid, 0)
    //stepUntilEqual(c.commandInQueue.valid, 0)
  }
  
  /*
   * Reads the next response from the core's peripheral bus. Returns the
   * response bits, and returns once cycle after (once the data has been
   * cleared from the queue).
   */
  def getResponse(desc: String = ""): BigInt = {
    //stepUntilEqual(c.io.respOut.valid, 1)
    val rtn = peek(c.io.respOut.bits)
    poke(c.io.respOut.ready, 1)
    step(1)
    poke(c.io.respOut.ready, 0)
    rtn
  }
  
  poke(c.io.commandIn.valid, 0)
  poke(c.io.respOut.ready, 0)
  
  println("Loading memory ... ")
  RiscvHelper.loadMem(this, c.core.imem.ispm, "../tests/examples/build/emulator/spi.inst.mem")
  RiscvHelper.loadMem(this, c.core.dmem.dspm, "../tests/examples/build/emulator/spi.data.mem")
  println("done")
  
  reset(5)  // TODO: justify the number
  
  /*sendCommand(0x01000000 | 10000);  // set clock
  sendCommand(0x02000000 | 0x0);    // set CPOL=0, CPHA=0
  sendCommand(0x00000000 | 0x4a);   // transfer data byte
  expectSpiHost(1000, 0, 8,
                c.io.gpio_out_broken(2), c.io.gpio_out_broken(0), c.io.gpio_in_broken(0),
                0, 0,
                Array(0, 1, 0, 0, 1, 0, 1, 0), Array(0, 1, 0, 1, 0, 0, 1, 0))
  expect(getResponse() == 0x52, "SPI response 0x4a => 0x52")
  
  sendCommand(0x01000000 | 1000);  // set clock
  sendCommand(0x02000000 | 0x3);    // set CPOL=1, CPHA=1
  sendCommand(0x00000000 | 0x2a);   // transfer data byte
  expectSpiHost(100, 0, 8,
                c.io.gpio_out_broken(2), c.io.gpio_out_broken(0), c.io.gpio_in_broken(0),
                1, 1,
                Array(0, 0, 1, 0, 1, 0, 1, 0), Array(1, 0, 1, 0, 1, 0, 1, 0))
  expect(getResponse() == 0xaa, "SPI response 0x4a => 0xAA")*/
  
  override def run() {
    val lol = reset {
      def f(cb: Unit=>Unit) {
        
      }
      shift { f }
      println("test")
      0
    }
    
    1+1
  }
} 
