// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, SteppedHWIOTester}

class MaxN(val n: Int, val w: Int) extends Module {

  private def Max2(x: UInt, y: UInt) = Mux(x > y, x, y)

  val io = IO(new Bundle {
    val ins = Vec(n, Input(UInt(w.W)))
    val out = Output(UInt(w.W))
  })
  io.out := io.ins.reduceLeft(Max2)
}

class MaxNTests(val n: Int, val w: Int) extends SteppedHWIOTester {
  val device_under_test = Module(new MaxN(n, w))
  val c = device_under_test

  val ins = Array.fill(c.n) {
    0
  }
  for (i <- 0 until 10) {
    var mx = 0
    for (i <- 0 until c.n) {
      ins(i) = rnd.nextInt(1 << c.w)
      poke(c.io.ins(i), ins(i))
      mx = if (ins(i) > mx) ins(i) else mx;
    }
    expect(c.io.out, mx)
    step(1)
  }
}

class MaxNSpec extends ChiselFlatSpec {
  "MaxN using reduction to connect things" should "build and run" in {
    assertTesterPasses{ new MaxNTests(10, 16) }
  }
}

