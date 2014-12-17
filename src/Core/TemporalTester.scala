package FlexpretTests

import Chisel._
import scala.collection.mutable.{Queue, PriorityQueue, HashMap, MutableList}
import scala.collection.immutable.{Iterable}
import scala.util.continuations._

import java.util.concurrent.{LinkedBlockingQueue, BlockingQueue}

class TemporalTesterThread[T <: Module](val c: T, val tester: TemporalTester[T]) {
  /**
   * Wrapper around run that automatically unblocks threads at the end and
   * does sanity checking.
   */
  def run_wrapper() {
    tester.resume(waitingQueue)
    
    run()
    if (!unblockedWaiters) {
      unblockWaiters()
    }
    
    tester.resume(waitingQueue)
  }
  
  /**
   * Actual testing code here. Overload this method.
   */
  def run() {
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
    tester.scheduleNewThread(newThread)
  }
  
  /**
   * Waits until the specified TemporalTesterThread has reached some point.
   * For now, this waits until the thread either returns or signals it is done.  
   */
  def waitOnThread(waitingOn: TemporalTesterThread[T]) {
    tester.waitOnThread(this, waitingOn)
    tester.resume(waitingQueue)
  }
  
  var unblockedWaiters = false
  var unblockedCycle: BigInt = -1
  /**
   * Unblocks any threads waiting on this thread. Must only be called once in
   * the thread's lifetime!
   */
  def unblockWaiters() {
    assert(!unblockedWaiters)
    unblockedWaiters = true
    unblockedCycle - getCycle()
    tester.threadUnblocking(this)
  }
  
  //
  // Various passthroughs for Tester functionality
  //
  def getCycle(): BigInt = {
    tester.cycle
  }
  
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
  
  def step(numCycles: BigInt) {
    // This involves the scheduler to wake up this thread in the specified
    // number of cycles, then returns control to the scheduler.
    tester.stepThread(this, numCycles)
    tester.resume(waitingQueue)
  }
  
  val waitingQueue = new LinkedBlockingQueue[Int]()
  def resume(myQueue: BlockingQueue[Int]) {
    assert(waitingQueue.isEmpty())
    assert(myQueue.isEmpty())
    waitingQueue.offer(0)
    myQueue.take()
  }
}

class WaitUntilEquals[T <: Module](c: T, tester: TemporalTester[T], 
    nodeValueMap: Iterable[(Bits, BigInt)]) 
  extends TemporalTesterThread[T](c, tester) {
  override def run() {
    while (!isMapEquals(nodeValueMap)) {
      step(1)
    }
  }
}

class ExpectEqualAround[T <: Module](c: T, tester: TemporalTester[T], 
    event: TemporalTester[T], nodeValueMap: Iterable[(Bits, BigInt)],
    cyclesBefore: BigInt, cyclesAfter: BigInt) 
  extends TemporalTesterThread[T](c, tester) {
  override def run() {
    var lastCycleTrue = -1
    
    while (!event.unblockedWaiters) {
      if (isMapEquals(nodeValueMap)) {
        lastCycleTrue = getCycle()
      }  else {
        lastCycleTrue = -1
      }
      step(1)
    }
    
    expect(lastCycleTrue <= event.unblockedCycle - cyclesBefore,
           "expected true before")
    
    var targetCycle = getCycle() + cyclesAfter
    while (getCycle() <= targetCycle) {
      expect(isMapEquals(nodeValueMap), "expected true after")
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
  val cycleThreadMap = new HashMap[BigInt, Queue[TemporalTesterThread[T]]]()
  // map of TemporalTesterThreads to list of continuations blocked by it 
  val threadBlockingMap = new HashMap[TemporalTesterThread[T], Queue[TemporalTesterThread[T]]]()
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
  
  def stepThread(thread: TemporalTesterThread[T], numCycles: BigInt) {
    val targetCycle = cycle + numCycles
    if (!cycleThreadMap.contains(targetCycle)) {
      nextActiveCycle += targetCycle
      cycleThreadMap += (targetCycle -> new Queue[TemporalTesterThread[T]]())
    }
    cycleThreadMap(targetCycle) += thread
  }
  
  def waitOnThread(thread: TemporalTesterThread[T], waitingOn: TemporalTesterThread[T]) {
    assert(threadBlockingMap.contains(waitingOn))
    assert(threadStatusMap.contains(waitingOn))
    if (threadStatusMap(waitingOn)) {
      assert(false, "attempted to wait on finished thread")
    }
    threadBlockingMap(waitingOn) += thread
  }
    
  def scheduleNewThread(newThread: TemporalTesterThread[T]) {
    assert(!threadBlockingMap.contains(newThread))
    assert(!threadStatusMap.contains(newThread))
    threadBlockingMap += (newThread -> new Queue[TemporalTesterThread[T]]())
    threadStatusMap += (newThread -> false)
    
    (new Thread(new Runnable {
      def run() {
        newThread.run_wrapper()
      }
    })).start
    waitingQueue.take()
    
    stepThread(newThread, 0)
  }
  
  def threadUnblocking(unblockingThread: TemporalTesterThread[T]) {
    assert(threadBlockingMap.contains(unblockingThread))
    assert(threadStatusMap.contains(unblockingThread))
    assert(threadStatusMap(unblockingThread) == false)
    threadStatusMap(unblockingThread) = true
    for (thread <- threadBlockingMap(unblockingThread)) {
      stepThread(thread, 0)
    }
  }
  
  def schedulerLoop() {
    while (!nextActiveCycle.isEmpty) {
      val targetCycle: BigInt = nextActiveCycle.dequeue()
      assert(targetCycle >= cycle)
      step((targetCycle - cycle).toInt)
      
      println(s"Scheduler: $targetCycle")
      
      val cycleQueue = cycleThreadMap(targetCycle)
      while (!cycleQueue.isEmpty) {
        val thread = cycleThreadMap(targetCycle).dequeue()
        thread.resume(waitingQueue)
      }
      cycleThreadMap -= targetCycle
    } 
  }
  
  val waitingQueue = new LinkedBlockingQueue[Int]()
  def resume(myQueue: BlockingQueue[Int]) {
    assert(waitingQueue.isEmpty())
    assert(myQueue.isEmpty())
    waitingQueue.offer(0)
    myQueue.take()
  }
}
