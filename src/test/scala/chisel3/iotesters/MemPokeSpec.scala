// See LICENSE for license details.

package chisel3.iotesters

import chisel3._
import org.scalacheck.Prop._
import org.scalatest.prop.Checkers

class InnerMemModule extends Module {
  val io: Bundle = IO(new Bundle)
  val nelly = Mem(1024, UInt(32.W))
}

class OuterMemModule extends Module {
  val io: Bundle = IO(new Bundle)
  val billy = Mem(1024, UInt(32.W))
  val inner = Module(new InnerMemModule)
}

class MemPokeTester(m: OuterMemModule) extends PeekPokeTester(m) {
  reset(10)
  0 until 1024 foreach { i =>
    pokeAt(m.billy, i, i)
    pokeAt(m.inner.nelly, value = i + 1, off = i)
  }
  step(10)
  0 until 1024 foreach { i =>
    expect(peekAt(m.billy, i) == BigInt(i), s"expected $i at $i, but found ${peekAt(m.billy, i)}")
    expect(peekAt(m.inner.nelly, i) == BigInt(i + 1), s"expected $i at $i, but found ${peekAt(m.billy, i)}")
  }
}

class MemPokeSpec extends ChiselFlatSpec with Checkers {
  behavior of "TestMem"

  it should "return peek values exactly as poked" in
    check(Driver.execute(Array(), () => new OuterMemModule) { m => new MemPokeTester(m) })
}
