package FlexpretTests
import Chisel._

import Core._
import FlexpretTests._

class IsaTest(c: CommandResponseQueueCore, memPrefix: String) extends TemporalTester(c, 50000000, 100000) {
  reset(5)  // TODO: justify the number
    
  RiscvHelper.loadMem(this, c.core.imem.ispm, memPrefix + ".inst.mem")
  RiscvHelper.loadMem(this, c.core.dmem.dspm, memPrefix + ".data.mem")
  
  while (peek(c.core.io.host.to_host) == 0) {
    step(1)
  }
  
  val dataToHost = peek(c.core.io.host.to_host)
  assert(dataToHost.isValidInt)
  dataToHost.intValue >> 30 match {
    case 0 =>
      if (dataToHost == 1) {
        println(s"**  ISA TEST: Passed (result=$dataToHost)")
      } else {
        println(s"**  ISA TEST: FAILED (result=$dataToHost)")
        ok = false  
      }
    case 1 =>
      // TODO: make this continue
      println("    ISA TEST: Threading message (???)")
      assert(false, "Implement me!")
    case 2 =>
      println("    ISA TEST: Timing message (???)")
      assert(false, "Implement me!")
    case 3 =>
      println("    ISA TEST: ???")
      assert(false, "Implement me!")
  }
  
  println(s"    ISA TEST: Total cycles: $cycle")
} 
