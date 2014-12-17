package FlexpretTests
import Chisel._

import Core._

class FlexPretTester(c: CommandResponseQueueCore) extends TemporalTester(c, 50000000, 100000) {
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
  
  /*
   * Sends a command to the core's peripheral bus. Returns once the command
   * queue is empty (i.e. the command has been read).
   */
  def sendCommand(command: BigInt, desc: String = "") {
    val msgHeader = s"sendCommand ($command): $desc"
    stepUntilEqual(c.io.commandIn.ready, 1)
    poke(c.io.commandIn.bits, command)
    poke(c.io.commandIn.valid, 1)
    step(1)
    poke(c.io.commandIn.valid, 0)
    stepUntilEqual(c.commandInQueue.valid, 0)
  }
  
  /*
   * Reads the next response from the core's peripheral bus. Returns the
   * response bits, and returns once cycle after (once the data has been
   * cleared from the queue).
   */
  def getResponse(desc: String = ""): BigInt = {
    stepUntilEqual(c.io.respOut.valid, 1)
    val rtn = peek(c.io.respOut.bits)
    poke(c.io.respOut.ready, 1)
    step(1)
    poke(c.io.respOut.ready, 0)
    rtn
  }
}
