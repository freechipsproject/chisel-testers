// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters.{SteppedHWIOTester, ChiselFlatSpec}

class DynamicMemorySearch(val n: Int, val w: Int) extends Module {
  val io = IO(new Bundle {
    val isWr   = Input(Bool())
    val wrAddr = Input(UInt(log2Ceil(n).W))
    val data   = Input(UInt(w.W))
    val en     = Input(Bool())
    val target = Output(UInt(log2Ceil(n).W))
    val done   = Output(Bool())
  })
  val index  = RegInit(0.U(log2Ceil(n).W))
  val list   = Mem(n, UInt(w.W))
  val memVal = list(index)
  val over   = !io.en && ((memVal === io.data) || (index === (n-1).asUInt))

  when(reset.toBool) {
    for(i <- 0 until n) {
      list(i) := 0.U
    }
  }
  when (io.isWr) {
    list(io.wrAddr) := io.data
  } .elsewhen (io.en) {
    index := 0.U
  } .elsewhen (over === false.B) {
    index := index + 1.U
  }
  io.done   := over
  io.target := index
}

class DynamicMemorySearchTests(val n: Int, val w: Int) extends SteppedHWIOTester {
  val device_under_test = Module(new DynamicMemorySearch(n, w))
  val c = device_under_test
  enable_all_debug = true

  val list = Array.fill(c.n)(0)
  rnd.setSeed(0L)

  for (k <- 0 until 16) {
    // WRITE A WORD
    poke(c.io.en, 0)
    poke(c.io.isWr, 1)
    val wrAddr = rnd.nextInt(c.n - 1)
    val data = rnd.nextInt((1 << c.w) - 1) + 1 // can't be 0
    poke(c.io.wrAddr, wrAddr)
    poke(c.io.data, data)
    step(1)
    list(wrAddr) = data
    // SETUP SEARCH
    val target = if (k > 12) rnd.nextInt(1 << c.w) else data
    poke(c.io.isWr, 0)
    poke(c.io.data, target)
    poke(c.io.en, 1)
    step(1)
    poke(c.io.en, 0)
    step(1)
    val expectedIndex = if (list.contains(target)) {
      list.indexOf(target)
    } else {
      list.length - 1
    }
    step(expectedIndex)
    expect(c.io.done, 1)
    expect(c.io.target, expectedIndex)
    step(1)
  }
}

class DynamicMemorySearchTester extends ChiselFlatSpec {
  val num_elements =  8
  val width        =  4
  "a dynamic memory search" should "be able to find things that were put in memory" in {
    assertTesterPasses {
      new DynamicMemorySearchTests(num_elements, width)
    }
  }
}
