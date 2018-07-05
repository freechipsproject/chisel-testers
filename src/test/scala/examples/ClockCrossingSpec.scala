// See LICENSE for license details.

package examples

// See LICENSE for license details.

import org.scalatest._
import chisel3._
import chisel3.experimental.withClock
import chisel3.iotesters.PeekPokeTester

class ClockCrossing extends Module {
  val io = IO(new Bundle {
    val divIn = Input(UInt(8.W))
    val mainOut = Output(UInt(8.W))
  })

  val divClock = RegInit(true.B)
  divClock := !divClock

  val divRegWire = Wire(UInt())
  withClock(divClock.asClock) {
    val divReg = RegNext(io.divIn, 1.U)
    divRegWire := divReg
  }

  val mainReg = RegNext(divRegWire, 0.U)
  io.mainOut := mainReg
}

class ClockCrossingTester(c: ClockCrossing) extends PeekPokeTester(c) {
  poke(c.io.divIn, 0x42)
  expect(c.io.mainOut, 0)  // initial register value
  step(1)
  expect(c.io.mainOut, 1)  // initial value of divReg
  step(1)  // for divided clock to have a rising edge
  expect(c.io.mainOut, 1)  // one-cycle-delaye divReg
  step(1)  // for main clock register to propagate
  expect(c.io.mainOut, 0x42)  // updated value propagates
}

class ClockCrossingSpec extends FreeSpec with Matchers {
   "test crossing from a 2:1 divider domain" in {
     iotesters.Driver.execute(Array("--backend-name", "verilator"), () => new ClockCrossing) { c =>
       new ClockCrossingTester(c)
     } should be (true)
  }
}
