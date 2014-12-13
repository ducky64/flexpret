package FlexpretTests
import Chisel._

import Core._
import FlexpretTests._

class IsaTest(c: CommandResponseQueueCore, memPrefix: String) extends TemporalTester(c, 50000000, 100000) {
  reset(5)  // TODO: justify the number
    
  RiscvHelper.loadMem(this, c.core.imem.ispm, memPrefix + ".inst.mem")
  RiscvHelper.loadMem(this, c.core.dmem.dspm, memPrefix + ".data.mem")
  
  stepUntilNotEqual(c.core.io.host.to_host, 0)
  
  val dataToHost = peek(c.core.io.host.to_host)
  assert(dataToHost.isValidInt)
  dataToHost.intValue >> 30 match {
    case 0 =>
      if (dataToHost == 1) {
        println("**  TEST: Passed: $dataToHost")
      } else {
        println(s"**  TEST: FAILED: $dataToHost")
        ok = false  
      }
    case 1 =>
      // TODO: make this continue
      println("    TEST: Threading message")
    case 2 =>
      println("    TEST: Timing message")
    case 3 =>
      println("    TEST: ???")
  }
  
  println(s"    TEST: Total cycles: $cycle")
} 
