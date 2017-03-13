// See LICENSE for license details.

package chisel3.iotesters

import org.scalatest.{FreeSpec, Matchers}

import chisel3._

class VF extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(8.W))
    val value = Output(UInt(8.W))
  })

  val vec = Wire(Vec(11, UInt(8.W)))
  vec := Vec(Seq.tabulate(11)(n => n.U))

  io.value := vec(io.addr)
}

class VFTester(c: VF) extends PeekPokeTester(c) {
  for(i <- 0 until 11) {
    poke(c.io.addr, i)
    expect(c.io.value, i)
    step(1)
  }
  for(i <- 11 until 111) {
    poke(c.io.addr, i)
    expect(c.io.value, i % 11)
    step(1)
  }
}

class VecFillSpec extends FreeSpec with Matchers {
  "should initialize vector" in {
    iotesters.Driver.execute(Array("-tiv"), () => new VF) { c =>
      new VFTester(c)
    } should be(true)
  }
}
