package FlexpretTests

import scala.io.Source
import Chisel._

object RiscvHelper {
  def loadMem[T <: Module, U <: Bits](tester:TemporalTester[T], mem:Mem[U], 
                                      filename:String) {
    var addr:Int = 0
    for (line:String <- Source.fromFile(filename).getLines()) {
      assert(line.length == 8)
      val value = BigInt(line, 16)
      tester.pokeAt(mem, value, addr)
      addr = addr + 1
    }
    println(s"Wrote $addr locations from $filename into memory.")
  }
}