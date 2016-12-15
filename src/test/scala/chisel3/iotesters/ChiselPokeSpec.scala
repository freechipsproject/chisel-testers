// See LICENSE for license details.

import chisel3._
import chisel3.iotesters.experimental.{ChiselPokeSpec, ChiselImplicitPokeSpec}

class MyDut extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  io.out := io.in + 1.U
}

class MyDutSpec extends ChiselPokeSpec {
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

class MyImplicitDutSpec extends ChiselImplicitPokeSpec {
  "MyDut with implicits" should "properly add" in {
    run(new MyDut) {implicit t => c =>
      c.io.in <<= 0x41
      step()
      assert(c.io.out ?== 0x42)

      c.io.in <<= 0x0
      step()
      assert(c.io.out ?== 0x1)
    }
  }
}
