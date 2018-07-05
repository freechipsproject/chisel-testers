// See LICENSE for license details.

package examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.{ChiselLoadMemoryAnnotation, log2Ceil}
import org.scalatest.{FreeSpec, Matchers}

class MemoryShape extends Bundle {
  val a = UInt(8.W)
  val b = SInt(8.W)
  val c = Bool()
}

class HasComplexMemory(memoryDepth: Int) extends Module {
  val io = IO(new Bundle {
    val address = Input(UInt(log2Ceil(memoryDepth).W))
    val value   = Output(new MemoryShape)
  })

  val memory = Mem(memoryDepth, new MemoryShape)

  chisel3.experimental.annotate(ChiselLoadMemoryAnnotation(memory, "test_run_dir/examples.LoadMemoryFromFileSpec1251342320/mem1.txt"))
  io.value := memory(io.address)
}

class HasComplexMemoryTester(c: HasComplexMemory) extends PeekPokeTester(c) {
  for(addr <- 0 until 8) {
    poke(c.io.address, addr)
    step(1)
    println(f"peek from $addr ${peek(c.io.value.a)}%x ${peek(c.io.value.b)}%x ${peek(c.io.value.c)}%x")
  }
}


class ComplexMemoryLoadingSpec extends  FreeSpec with Matchers {
  "memory loading should be possible with complex memories" in {
    val result = iotesters.Driver.execute(
      args = Array("--backend-name", "verilator"),
      dut = () => new HasComplexMemory(memoryDepth = 8)
    ) { c =>
      new HasComplexMemoryTester(c)
    }
  }
}
