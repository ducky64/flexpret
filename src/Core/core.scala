/******************************************************************************
flexpret.scala:
  Configurable precision-timed (PRET) 5-stage RISC-V processor.
  - Single-thread
  - Multi-threaded (4-8) with fixed round-robin thread scheduling
  - Multi-threaded (2-8) with flexible round-robin thread scheduling
Authors: 
  Michael Zimmer (mzimmer@eecs.berkeley.edu)
  Chris Shaver (shaver@eecs.berkeley.edu)
  Hokeun Kim (hokeunkim@eecs.berkeley.edu)
Acknowledgement:
  Based on Sodor single-thread 5-stage RISC-V processor by Christopher Celio.
  https://github.com/ucb-bar/riscv-sodor/
******************************************************************************/

package Core
{

import Chisel._
import Node._
import Common._
import CoreConstants._
import collection.mutable.{ArrayBuffer}

// TODO cleaner
case class CoreConfig(threads: Int, flex: Boolean, 
    iSpmKBytes: Int, dSpmKBytes: Int, mulStages: Int, 
    stats: Boolean, exceptions: Boolean, 
    getTime: Boolean, delayUntil: Boolean, 
    exceptionOnExpire: Boolean)
{
  val threadBits = log2Up(threads)
  val iSpmAddrBits = log2Up(1024/4*iSpmKBytes)
  val dSpmAddrBits = log2Up(1024/4*dSpmKBytes)
  val dSpmPageIndex = 10
  val dSpmPageBits = dSpmAddrBits-dSpmPageIndex

  // Use require() to dependencies between configurations.
  require(mulStages >= 1 && mulStages <= 2)
  // If excectionOnExpire, then exceptions and getTime
  require(!exceptionOnExpire || (exceptions && getTime))
  // If delayUntil, then getTime
  require(!delayUntil || getTime)

}

// TODO: add mechanism to write to I-SPM.
class CoreIo(iSpmAddrBits: Int) extends Bundle
{
  //val ispm_write = Bool(INPUT)
  //val ispm_waddr = UInt(INPUT, iSpmAddrBits)
  //val ispm_wdata = Bits(INPUT, XPRLEN)
  val host       = new HostIo()
  val bus      = new MemIo(32).flip
  val exe_ns_clock = Bits(OUTPUT, 64)
}

class Core(conf: CoreConfig) extends Module
{
  val io = new CoreIo(conf.iSpmAddrBits)
  
  val c  = Module(new Control(conf)) 
  val d  = Module(new Datapath(conf))
  val ispm = Module(new ISpm(conf))
  val dspm = Module(new DSpm(conf))

  // Connect datapath and control unit.
  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat
  
  // Connect datapath to scratchpad memories.
  d.io.ispm <> ispm.io
  d.io.dspm <> dspm.io

  // Connect datapath to external IO
  d.io.top <> io
  
}

object CoreMain {
   def main(args: Array[String]): Unit = { 
      
      // Processor configuration is passed in using args from sbt.
      val confString = args(0)
      val chiselArgs = args.slice(1, args.length)

      var threads = 4
      var flex = true
      var iSpmKBytes = 8
      var dSpmKBytes = 8
      var mulStages = 2
      """(\d+)t(.*)-(\d+)i-(\d+)d-(\d+)smul""".r.findAllIn(confString).matchData foreach {
        m => 
          threads = m.group(1).toInt
          flex = m.group(2) contains "f"
          iSpmKBytes = m.group(3).toInt
          dSpmKBytes = m.group(4).toInt
          mulStages = m.group(5).toInt
      }
      val stats = confString contains "stats"
      val exceptions = confString contains "exc"
      val getTime = confString contains "gt"
      val delayUntil = confString contains "du"
      val exceptionOnExpire = confString contains "ee"

      val coreConfig = CoreConfig(threads, flex, iSpmKBytes, dSpmKBytes, 
        mulStages, stats, exceptions, getTime, delayUntil, exceptionOnExpire)


//val coreConfig = CoreConfig(4, true, 8, 8, 1, true, true, true, true, false)
      //val coreConfig = CoreConfig(args(0).toInt, 
      //                            args(1).toBoolean, 
      //                            args(2).toInt,
      //                            args(3).toInt,
      //                            args(4).toInt,
      //                            args(5).toBoolean,
      //                            false,
      //                            false,
      //                            false,
      //                            false)
                                  //true,
                                  //true,
                                  //true,
                                  //true)

      //// Pass configuration to FlexPRET processor.
      chiselMain( chiselArgs, () => Module(new Core(coreConfig)) )

      // Processor configuration is passed in using args from sbt.
      //val chiselArgs = args.slice(6, args.length)

      //val coreConfig = CoreConfig(args(0).toInt, 
      //                            args(1).toBoolean, 
      //                            args(2).toInt,
      //                            args(3).toInt,
      //                            args(4).toInt,
      //                            args(5).toBoolean)

      //// Pass configuration to FlexPRET processor.
      //chiselMain( chiselArgs, () => new Core(coreConfig) )
   }
}

}
