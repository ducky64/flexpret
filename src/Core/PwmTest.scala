package FlexpretTests
import Chisel._

import Core._
import FlexpretTests._

class PwmTest(c: CommandResponseQueueCore) extends FlexPretTester(c) {
  poke(c.io.commandIn.valid, 0)
  poke(c.io.respOut.ready, 0)
  
  println("Loading memory ... ")
  RiscvHelper.loadMem(this, c.core.imem.ispm, "../tests/examples/build/emulator/pwm.inst.mem")
  RiscvHelper.loadMem(this, c.core.dmem.dspm, "../tests/examples/build/emulator/pwm.data.mem")
  println("done")
  
  reset(5)  // TODO: justify the number
  
  val pwmLine = c.io.gpio_out_broken(0)
  val pwmCycleFactor = 64  // number of cycles per PWM time "unit"
  
  sendCommand(0x00000000 | 40);    // set period
  sendCommand(0x01000000 | 10);    // set high time
  
  stepUntilEqual(pwmLine, 1)
  val timer = createTimerAtNow()

  timer.expectEqualsAtCentered(pwmLine, 0, 1*pwmCycleFactor, 0,
                               desc="10/40 PWM high")
  timer.expectEqualsAtCentered(pwmLine, 1, 3*pwmCycleFactor, 0,
                               desc="10/40 PWM low")
                            
  // pipeline the next command - all high
  sendCommand(0x01000000 | 40);
                               
  timer.expectEqualsAtCentered(pwmLine, 0, 1*pwmCycleFactor, 0,
                               desc="10/40 PWM high")
  timer.expectEqualsAtCentered(pwmLine, 1, 3*pwmCycleFactor, 0,
                               desc="10/40 PWM low")
                               
  // pipeline the next command - all low
  sendCommand(0x01000000 | 0);  
  timer.expectEqualsAtCentered(pwmLine, 0, 2*4*pwmCycleFactor, 0,
                               desc="2 * 40/40 PWM high")
                               

  // pipeline the next command - half duty cycle
  sendCommand(0x01000000 | 20);
  timer.expectEqualsAtCentered(pwmLine, 1, 2*4*pwmCycleFactor, 0,
                               desc="2 * 40/40 PWM low")
                               
  timer.expectEqualsAtCentered(pwmLine, 0, 2*pwmCycleFactor, 0,
                               desc="10/20 PWM high")
  timer.expectEqualsAtCentered(pwmLine, 1, 2*pwmCycleFactor, 0,
                               desc="10/20 PWM low")
} 
