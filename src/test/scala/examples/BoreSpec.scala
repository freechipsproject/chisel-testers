// See README.md for license details.

package examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FreeSpec, Matchers}

class Constant extends MultiIOModule {
  val x = Reg(UInt(6.W))
  x := 42.U
}

class Expect extends MultiIOModule {
  val y = IO(Output(UInt(6.W)))
  y := 0.U
}

class BoreTop extends MultiIOModule {
  val y = IO(Output(UInt(6.W)))
  val constant = Module(new Constant)
  val expect = Module(new Expect)
  y := expect.y

  util.experimental.BoringUtils.bore(constant.x, Seq(expect.y))
}

class BoreSpec extends FreeSpec with Matchers {
  "Boring utils should work in io.testers" in {
    iotesters.Driver(() => new BoreTop) { c =>
      new PeekPokeTester(c) {
        expect(c.y, expected = 42)
      }
    } should be(true)
  }
}
