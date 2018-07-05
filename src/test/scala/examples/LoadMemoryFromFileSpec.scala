// See LICENSE for license details.

package examples

// See LICENSE for license details.

import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.ChiselLoadMemoryAnnotation
import firrtl.FirrtlExecutionSuccess
import org.scalatest.{FreeSpec, Matchers}

//noinspection TypeAnnotation
class UsesMem(memoryDepth: Int, memoryType: Bits) extends Module {
  val io = IO(new Bundle {
    val address = Input(UInt(memoryType.getWidth.W))
    val value   = Output(memoryType)
    val value2  = Output(memoryType)
  })

  val memory = Mem(memoryDepth, memoryType)

  chisel3.experimental.annotate(ChiselLoadMemoryAnnotation(memory, "test_run_dir/examples.LoadMemoryFromFileSpec1251342320/mem1.txt"))
  io.value := memory(io.address)

  val low = Module(new UsesMemLow(memoryDepth, memoryType))

  low.io.address := io.address
  io.value2 := low.io.value
}

class UsesMemLow(memoryDepth: Int, memoryType: Data) extends Module {
  val io = IO(new Bundle {
    val address = Input(UInt(memoryType.getWidth.W))
    val value   = Output(memoryType)
  })

  val memory = Mem(memoryDepth, memoryType)

  chisel3.experimental.annotate(ChiselLoadMemoryAnnotation(memory, "test_run_dir/examples.LoadMemoryFromFileSpec1251342320/mem2.txt"))
  io.value := memory(io.address)
}

class LoadMemoryFromFileTester(c: UsesMem) extends PeekPokeTester(c) {
  for(addr <- 0 until 8) {
    poke(c.io.address, addr)
    step(1)
    println(f"peek from $addr ${peek(c.io.value)}%x ${peek(c.io.value2)}%x")
  }
}

class LoadMemoryFromFileSpec extends FreeSpec with Matchers {
  "Users can specify a source file to load memory from" in {
    val result = iotesters.Driver.execute(
      args = Array("--backend-name", "verilator"),
      dut = () => new UsesMem(memoryDepth = 8, memoryType = UInt(16.W))
    ) { c =>
      new LoadMemoryFromFileTester(c)
    }
  }
}
