// See LICENSE for license details.

package chisel3.iotesters

import chisel3._
import chisel3.util.log2Ceil
import org.scalacheck.Prop._
import org.scalatest.prop.Checkers

class InnerMemModule extends Module {
  //noinspection TypeAnnotation
  val io = IO(new Bundle)
  val nelly = Mem(1024, UInt(32.W))
}

class OuterMemModule extends Module {
  //noinspection TypeAnnotation
  val io = IO(new Bundle {
    val readAddress = Input(UInt(log2Ceil(1024).W))
    val readData    = Output(UInt(32.W))
  })
  val billy = Mem(1024, UInt(32.W))
  val inner = Module(new InnerMemModule)

  io.readData := billy(io.readAddress)
}

class MemPokeTester(m: OuterMemModule) extends PeekPokeTester(m) {
  reset(10)
  0 until 1024 foreach { i =>
    pokeAt(m.billy, i, i)
    pokeAt(m.inner.nelly, value = i + 1, off = i)
  }

  step(10)

  // This uses direct access reading
  0 until 1024 foreach { i =>
    expect(peekAt(m.billy, i) == BigInt(i), s"expected $i at $i, but found ${peekAt(m.billy, i)}")
    expect(peekAt(m.inner.nelly, i) == BigInt(i + 1), s"expected $i at $i, but found ${peekAt(m.billy, i)}")
  }

  // This shows that the ordinary memory systems sees the values written with pokeAt
  0 until 1024 foreach { i =>
    poke(m.io.readAddress, i)
    step(1)

    expect(peek(m.io.readData) == BigInt(i), s"expected $i at $i, but found ${peek(m.io.readData)}")
  }


}

class MemPokeSpec extends ChiselFlatSpec with Checkers {
  behavior of "Peeking and Poking straight into underlying memory, in interpreter"

  it should "return peek values exactly as poked" in
    check(Driver.execute(Array(), () => new OuterMemModule) { m => new MemPokeTester(m) })
}
