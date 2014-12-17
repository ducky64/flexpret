package FlexpretTests

import Chisel._
import scala.collection.mutable.{PriorityQueue, HashMap, MutableList}
import scala.collection.immutable.{Iterable}
import scala.util.continuations._

class TemporalTesterThread[T <: Module](val c: T, val tester: TemporalTester[T]) {
  /**
   * Wrapper around run that automatically unblocks threads at the end and
   * does sanity checking.
   */
  def run_wrapper(): Unit @suspendable = {
    run()
    if (!unblockedWaiters) {
      unblockWaiters()
    }
  }
  
  /**
   * Actual testing code here. Overload this method.
   */
  def run(): Unit @suspendable = {
    step(1)
  }
  
  //
  // Helper functionality
  //
  /**
   * Returns true of all nodes in the map are equal to their values.
   * @param[in] desc a string description to be printed out with errors.
   */
  def isMapEquals(nodeValueMap: Iterable[(Bits, BigInt)]): Boolean = {
    for ((node, value) <- nodeValueMap) {
      if (peek(node) != value) {
        return false
      }
    }
    return true
  }
  
  /**
   * Expect that all nodes in the map are equal to their values.
   * @param[in] desc a string description to be printed out with errors.
   */
  def expectMapEquals(nodeValueMap: Iterable[(Bits, BigInt)], desc: String = "") {
    tester.expect(isMapEquals(nodeValueMap),
                  s"expectMapEquals: $desc: not equal")
  }
  
  //
  // Thread control functionality
  //
  /**
   * Creates and schedules a new TemporalTesterThread, which runs in "parallel"
   * with this thread.
   */
  def newThread(newThread: TemporalTesterThread[T]) {
    assert(newThread.tester == tester)
    tester.scheduleNewThread(newThread)
  }
  
  /**
   * Waits until the specified TemporalTesterThread has reached some point.
   * For now, this waits until the thread either returns or signals it is done.  
   */
  def waitOnThread(waitingOn: TemporalTesterThread[T]): Unit @suspendable = {
    shift(tester.scheduleBlocking(waitingOn))
  }
  
  var unblockedWaiters = false
  /**
   * Unblocks any threads waiting on this thread. Must only be called once in
   * the thread's lifetime!
   */
  def unblockWaiters() {
    assert(!unblockedWaiters)
    unblockedWaiters = true
    tester.threadUnblocking(this)
  }
  
  //
  // Various passthroughs for Tester functionality
  //
  def peek(node: Bits): BigInt = {
    tester.peek(node)
  }
  
  def poke(node: Bits, value: BigInt) {
    tester.poke(node, value)
  }
  
  def expect(condition: Boolean) {
    // TODO: specifiable desc
    tester.expect(condition, "")
  }
  
  def expect(node: Bits, value: BigInt) {
    // TODO: pass descriptions to tester
    tester.expect(node, value)
  }
  
  def step(numCycles: BigInt): Unit @suspendable = {
    // This involves the scheduler to wake up this thread in the specified
    // number of cycles, then returns control to the scheduler.
    shift(tester.scheduleContinuation(numCycles))
  }
}

class WaitUntilEquals[T <: Module](c: T, tester: TemporalTester[T], 
    nodeValueMap: Iterable[(Bits, BigInt)]) 
  extends TemporalTesterThread[T](c, tester) {
  override def run(): Unit @suspendable = {
    while (!isMapEquals(nodeValueMap)) {
      println(s"Step ${tester.cycle}")
      step(1)
    }
  }
}

// TODO: figure out covariance / contravariance (why +T?)
class TemporalTester[T <: Module](c: T, val frequency:Int,
                                   val max_cycle: BigInt = 0,
                                   isTrace: Boolean = false)
                                   extends Tester(c, isTrace) {
  var cycle: BigInt = 0
  // next cycle where there is activity in at least one thread
  val nextActiveCycle = new PriorityQueue[BigInt]()
  // map of active cycles to list of continuations to run on that cycle
  val cycleContinuationMap = new HashMap[BigInt, MutableList[Unit=>Unit]]()
  // map of TemporalTesterThreads to list of continuations blocked by it 
  val threadBlockingMap = new HashMap[TemporalTesterThread[T], MutableList[Unit=>Unit]]()
  // map of TemporalTesterThreads to whether it has "finished" or not
  val threadStatusMap = new HashMap[TemporalTesterThread[T], Boolean]()
  
  override def step(n: Int) {
    cycle = cycle + n
    if (cycle > max_cycle) {
      println(s"TemporalTester: cycle count limit exceeded: $cycle > $max_cycle")
      assert(false)  // TODO: better failure mode
    }
    super.step(n)
  }
  
  def scheduleContinuation(numCycles: BigInt): ((Unit => Unit) => Unit) = {
    val targetCycle = cycle + numCycles
    def scheduleThreadInternal(continuation: Unit => Unit) {
      if (!cycleContinuationMap.contains(targetCycle)) {
        cycleContinuationMap += (targetCycle -> new MutableList[Unit => Unit]())
      }
      cycleContinuationMap(targetCycle) += continuation 
    }
    scheduleThreadInternal
  }
  
  def scheduleBlocking(waitingOn: TemporalTesterThread[T]): ((Unit => Unit) => Unit) = {
    def scheduleBlockingInternal(continuation: Unit => Unit) {
      assert(threadBlockingMap.contains(waitingOn))
      assert(threadStatusMap.contains(waitingOn))
      if (threadStatusMap(waitingOn)) {
        assert(false, "attempted to wait on finished thread")
      }
      threadBlockingMap(waitingOn) += continuation
    }
    scheduleBlockingInternal
  }
  
  def startThread(newThread: TemporalTesterThread[T]) = {
    scheduleNewThread(newThread)
    reset {
      newThread.run_wrapper()
      0
    }
  }
  
  def scheduleNewThread(newThread: TemporalTesterThread[T]) {
    assert(!threadBlockingMap.contains(newThread))
    assert(!threadStatusMap.contains(newThread))
    threadBlockingMap += (newThread -> new MutableList[Unit => Unit]())
    threadStatusMap += (newThread -> false)
  }
  
  def threadUnblocking(unblockingThread: TemporalTesterThread[T]) {
    assert(threadBlockingMap.contains(unblockingThread))
    assert(threadStatusMap.contains(unblockingThread))
    assert(threadStatusMap(unblockingThread) == false)
    threadStatusMap(unblockingThread) = true
    for (thread <- threadBlockingMap(unblockingThread)) {
      scheduleContinuation(0)(thread)
    }
  }
}
