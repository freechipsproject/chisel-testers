// SPDX-License-Identifier: Apache-2.0

package verilator

import chisel3._
import chisel3.iotesters._

class ZeroWidthIOModule extends Module {
  val io = IO(new Bundle {
    val zeroIn   = Input(UInt(0.W))
    val zeroOut  = Output(UInt(0.W))

    val otherIn  = Input(UInt(3.W))
    val otherOut = Output(UInt(3.W))
  })

  io.zeroOut  := io.zeroIn
  io.otherOut := io.otherIn
}

class ZeroWidthIOTester(c: ZeroWidthIOModule) extends PeekPokeTester(c) {
  poke(c.io.otherIn, 3)
  expect(c.io.otherOut, 3)
}
