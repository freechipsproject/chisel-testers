// See LICENSE for license details.

import chisel3._
import chisel3.iotesters.experimental.{PokeTester, ImplicitPokeTester}

import org.scalatest._

class MyDut extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  io.out := io.in + 1.U
}

class PokeTesterSpec extends FlatSpec with PokeTester {
  "MyDut" should "properly add" in {
    run(new MyDut) {(t, c) =>
      t.poke(c.io.in, 0x41)
      t.step()
      t.expect(c.io.out, 0x42)

      t.poke(c.io.in, 0x0)
      t.step()
      t.expect(c.io.out, 0x1)
    }
  }
}

class ImplicitPokeTesterSpec extends FlatSpec with ImplicitPokeTester {
  "MyDut with implicits" should "properly add" in {
    run(new MyDut) {implicit t => c =>
      c.io.in <<= 0x41
      step()
      c.io.out ?== 0x42

      c.io.in <<= 0x0
      step()
      c.io.out ?== 0x1
    }
  }
}
