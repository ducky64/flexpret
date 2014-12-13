/******************************************************************************
File: core.scala
Description: FlexPRET Processor (configurable 5-stage RISC-C processor)
Author: Michael Zimmer (mzimmer@eecs.berkeley.edu)
Contributors: 
License: See LICENSE.txt
******************************************************************************/
package Core

import Chisel._
import FlexpretConstants._
import FlexpretTests._

case class FlexpretConfiguration(threads: Int, iMemKB: Int, dMemKB: Int, exceptions: Boolean)
{
  
  val threadBits = log2Up(threads)

  // RegisterFile
  val regDepth = 32*threads

  // ISpm
  val iMemDepth = 256*iMemKB  // 32-bit entries
  val iMemAddrBits = log2Up(iMemDepth) // word addressable

  // DSpm
  val dMemDepth = 256*dMemKB //32-bit entries
  val dMemAddrBits = log2Up(4*dMemDepth) // byte addressable

  // GPIO
  val gpiBits = 8
  val gpoBits = 8

  // Bus
  val busAddrBits = 32

  // Scheduler
  val initialSlots = List(
    SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_T0
  )
  val initialTmodes = (0 until threads).map(i => if(i != 0) TMODE_HZ else TMODE_HA)


  // functionality
  val timeBits = 32
  val timeInc = 10
  require(timeBits <= 32) // TODO: support up to 64 bits
  val getTime = true
  val delayUntil = true
  // val systemCounters = true
 
  // design exploration
  val dedicatedBranchCheck = true

}

class InstMemBusIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  // write port
  val addr = UInt(INPUT, conf.iMemAddrBits)
  val write = Bool(INPUT)
  val data_in = Bits(INPUT, 32)
  val ready = Bool(OUTPUT)
  // for read/write port
  //val enable = Bool(INPUT)
  //val data_out = Bits(OUTPUT, 32)
}

class DataMemBusIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  // read/write port
  val addr = UInt(INPUT, conf.dMemAddrBits-2) // assume word aligned
  val enable = Bool(INPUT)
  val data_out = Bits(OUTPUT, 32)
  val byte_write = Vec.fill(4) { Bool(INPUT) }
  val data_in = Bits(INPUT, 32)
}

class BusIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  val addr = UInt(INPUT, conf.busAddrBits) // assume word aligned
  val enable = Bool(INPUT)
  val data_out = Bits(OUTPUT, 32)
  val write =  Bool(INPUT)
  val data_in = Bits(INPUT, 32)
}

class HostIO() extends Bundle 
{
  val to_host = Bits(OUTPUT, 32)
}

class GPIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  val in = Vec.fill(conf.threads) { Bits(INPUT, conf.gpiBits) }
  val out = Vec.fill(conf.threads) { Bits(OUTPUT, conf.gpoBits) }
}

class CoreIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  val imem = new InstMemBusIO()
  val dmem = new DataMemBusIO()
  val bus  = new BusIO().flip
  val host = new HostIO()
  val gpio = new GPIO()
}

class Core(confIn: FlexpretConfiguration) extends Module
{

  implicit val conf = confIn
  
  val io = new CoreIO()

  val control = Module(new Control())
  val datapath = Module(new Datapath())
  val imem = Module(new ISpm())
  val dmem = Module(new DSpm())
 
  // internal
  datapath.io.control <> control.io
  datapath.io.imem <> imem.io.core
  datapath.io.dmem <> dmem.io.core

  // external
  io.imem <> imem.io.bus
  io.dmem <> dmem.io.bus
  io.bus  <> datapath.io.bus
  io.host <> datapath.io.host
  io.gpio <> datapath.io.gpio

}

class CommandResponseQueueCoreIO(implicit conf: FlexpretConfiguration) extends Bundle
{
  val gpio = new GPIO()
  val commandIn = Decoupled(UInt(32))      // from external to Core
  val respOut = Decoupled(UInt(32)).flip   // from Core to external
}

class CommandResponseQueueCore(confIn: FlexpretConfiguration) extends Module
{
  implicit val conf = confIn
  
  val core = new Core(confIn) 
  val io = new CommandResponseQueueCoreIO()
  
  // Host interface data queues & definitions
  val COMMAND_IN_DATA_ADDR = UInt(0xffff8800)
  val COMMAND_IN_VALID_ADDR = UInt(0xffff8801)
  val commandInQueue = Queue(io.commandIn, 1)
  
  val respOutEnqIo = Decoupled(UInt(0, 32))
  val RESP_OUT_DATA_ADDR = UInt(0xffff8810)
  val RESP_OUT_READY_ADDR = UInt(0xffff8811)
  val respOutQueue = Queue(respOutEnqIo, 1)
  respOutQueue <> io.respOut
  
  // Connections to bus IO
  when (core.io.bus.enable) {
    switch(core.io.bus.addr) {
      is(COMMAND_IN_DATA_ADDR) {
        assert(io.commandIn.valid, "attempted read from invalid cmd data")
        assert(!core.io.bus.write, "attempted write to cmd valid")
        commandInQueue.ready := Bool(true) 
        respOutEnqIo.valid := Bool(false)
        core.io.bus.data_in := commandInQueue.bits
      }
      is(COMMAND_IN_VALID_ADDR) {
        assert(!core.io.bus.write, "attempted write to cmd valid")
        commandInQueue.ready := Bool(false)
        respOutEnqIo.valid := Bool(false)
        core.io.bus.data_in := commandInQueue.valid
      }
      is(RESP_OUT_DATA_ADDR) {
        assert(respOutEnqIo.ready, "attempted write to full resp data")
        assert(core.io.bus.write, "attempted read from resp data")     
        commandInQueue.ready := Bool(false)
        respOutEnqIo.valid := Bool(true)
        respOutEnqIo.bits := core.io.bus.data_out
      }
      is(RESP_OUT_READY_ADDR) {
        assert(!core.io.bus.write, "attempted write to resp ready")
        commandInQueue.ready := Bool(false)
        respOutEnqIo.valid := Bool(false)
        core.io.bus.data_in := respOutEnqIo.ready
      }
    }
  }
}

object CoreMain {
  def main(args: Array[String]): Unit = {
    val runArg = args(0)
    var chiselArgs = args.slice(1, args.length)
    var coreConfig = new FlexpretConfiguration(4, 16, 16, true)
    
    // TODO: FIXME: Add parameter parsing back in.
    /*if (chiselArgs.length > 0) {
      val confString = chiselArgs(0)
      val parsed = """(\d+)t(.*)-(\d+)i-(\d+)d""".r.findFirstMatchIn(confString)
      // TODO: print error/warning message
      coreConfig = FlexpretConfiguration(parsed.get.group(1).toInt,
                                         parsed.get.group(3).toInt,
                                         parsed.get.group(4).toInt,
                                         true)
      chiselArgs = chiselArgs.slice(1, args.length)
    }*/
    runArg match {
      case "spiTest" =>
        chiselMainTest(chiselArgs, () => Module(new Core(coreConfig))){
          c => new SpiTest(c)}
      case "isaTest" =>
        chiselMainTest(chiselArgs, () => Module(new Core(coreConfig))){
          c => new IsaTest(c, "../tests/isa/build/emulator/addi")}
      case _ =>
        println("default, running ChiselMain")
        chiselMain(chiselArgs, () => Module(new Core(coreConfig)))
    }
  }
}
