// See LICENSE for license details.

package examples

import chisel3._
import chisel3.experimental.MultiIOModule
import chisel3.util._
import chisel3.iotesters._
import org.scalatest.{Matchers, FlatSpec}


class MultiIOAdder extends MultiIOModule {
  val a = IO(Input(UInt(4.W)))
  val b = IO(Input(UInt(4.W)))
  val c = IO(Output(UInt(5.W)))

  c := a +& b
}

class MultiIOAdderTester(c: MultiIOAdder) extends PeekPokeTester(c) {
  for {
    i <- 0 until 15
    j <- 0 until 15
  } {
    poke(c.a, i)
    poke(c.b, j)
    expect(c.c, i + j)
  }
}

class MultiIOModuleSpec extends FlatSpec with Matchers {
  behavior of "MuiltiIOAdder"

  it should "test correctly for every i/o combination with verilator" in {
    val args = Array("--backend-name", "verilator")
    iotesters.Driver.execute(args, () => new MultiIOAdder) { c =>
      new MultiIOAdderTester(c)
    } should be (true)
  }
  it should "test correctly for every i/o combination with firrtl" in {
    val args = Array("--backend-name", "firrtl")
    iotesters.Driver.execute(args, () => new MultiIOAdder) { c =>
      new MultiIOAdderTester(c)
    } should be (true)
  }
}
