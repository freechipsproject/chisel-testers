// SPDX-License-Identifier: Apache-2.0

package examples

import java.nio.file.{Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util.log2Ceil
import firrtl.FileUtils
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

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

  loadMemoryFromFile(memory, "test_run_dir/complex_mem_test/mem")

  io.value := memory(io.address)
}

class HasComplexMemoryTester(c: HasComplexMemory) extends PeekPokeTester(c) {
  var boolValue: Int = 0
  for(addr <- 0 until 8) {
    poke(c.io.address, addr)
    step(1)
    println(f"peek from $addr ${peek(c.io.value.a)}%x ${peek(c.io.value.b)}%x ${peek(c.io.value.c)}%x")
    expect(c.io.value.a, addr)
    expect(c.io.value.b, 7 - addr)
    expect(c.io.value.c, boolValue)
    boolValue = 1 - boolValue
  }
}


class ComplexMemoryLoadingSpec extends AnyFreeSpec with Matchers {
  "memory loading should be possible with complex memories" - {

    val targetDirName = "test_run_dir/complex_mem_test"
    FileUtils.makeDirectory(targetDirName)

    val path1 = Paths.get(targetDirName + "/mem_a")
    val path2 = Paths.get(targetDirName + "/mem_b")
    val path3 = Paths.get(targetDirName + "/mem_c")

    Files.copy(getClass.getResourceAsStream("/mem1.txt"), path1, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/mem2.txt"), path2, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/mem3.txt"), path3, REPLACE_EXISTING)

    "should work with treadle" in {
      iotesters.Driver.execute(
        args = Array("--backend-name", "treadle", "--target-dir", targetDirName, "--top-name", "complex_mem_test"),
        dut = () => new HasComplexMemory(memoryDepth = 8)
      ) { c =>
        new HasComplexMemoryTester(c)
      } should be(true)
    }

    "should work with verilator" in {
      iotesters.Driver.execute(
        args = Array("--backend-name", "verilator", "--target-dir", targetDirName, "--top-name", "complex_mem_test"),
        dut = () => new HasComplexMemory(memoryDepth = 8)
      ) { c =>
        new HasComplexMemoryTester(c)
      } should be(true)
    }
  }
}
