// See LICENSE for license details.

package examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FreeSpec, Matchers}

/**
  * This provides an example of how to re-run a verilator compiled simulation without re-running verilator
  * Currently this method does re-elaborate the circuit
  */
class RerunWithoutElaboratonAndCompileSpec extends FreeSpec with Matchers {
  "Demonstrate how to re-run a given test without recompiling" - {
    "build once" in {
      iotesters.Driver.execute(
        Array("--backend-name", "verilator", "--target-dir", "build1", "--top-name", "PlusOne"), () => new PlusOne
//        Array("--backend-name", "verilator"), () => new PlusOne
      ) { c =>
        new PlusOneTester(c, 0)
      } should be (true)
    }
    "run again" in {
      iotesters.Driver.run(() => new PlusOne, "./build1/VPlusOne") { c =>
        new PlusOneTester(c, 33333)
      }
    }
  }
}

class PlusOneTester(c: PlusOne, start_number: Int) extends PeekPokeTester(c) {
  for(i <- start_number to start_number + 10000) {
    poke(c.io.dog, i)
    poke(c.io.cat, i)
    poke(c.io.fox, i)
    poke(c.io.asp, i)
    expect(c.io.out, i + 1)
  }
}

class PlusOne extends Module {
  val io = IO(new Bundle {
    val dog = Input(UInt(64.W))
    val cat = Input(UInt(64.W))
    val asp= Input(UInt(64.W))
    val fox = Input(UInt(64.W))
    val out = Output(UInt(64.W))
  })

  io.out := io.fox +% 1.U
}
