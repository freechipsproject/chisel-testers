// See LICENSE for license details.

package examples

import org.scalatest.{ Matchers, FlatSpec}

import chisel3._
import chisel3.iotesters._

class HasCycle extends Module {
  val io = IO( new Bundle {
    val a = Input(Bool())
    val o = Output(Bool())
  })

  val b = Wire(Bool())
  b := b&&io.a

  io.o := b
}

class HasCycleTester( c:HasCycle) extends PeekPokeTester(c) {
  poke( c.io.a, 0)
  step(1)
}

class HasCycleTest extends FlatSpec with Matchers {
  behavior of "HasCycle"

  it should "work in the interpreter" in {
    chisel3.iotesters.Driver.execute(
      //        Array( "--no-check-comb-loops", "--backend-name", "verilator"),
      //        Array( "--no-check-comb-loops", "--backend-name", "firrtl", "-filcol"),
      Array( "--no-check-comb-loops", "--backend-name", "firrtl", "-fisfas", "-fiac"),
      () => new HasCycle) { c =>
      new HasCycleTester( c)
    } should be ( true)
  }
  it should "work in verilator" in {
    chisel3.iotesters.Driver.execute(
              Array( "--no-check-comb-loops", "--backend-name", "verilator"),
//              Array( "--no-check-comb-loops", "--backend-name", "firrtl", "-filcol"),
//      Array( "--no-check-comb-loops", "--backend-name", "firrtl", "-fisfas", "-fiac"),
      () => new HasCycle) { c =>
      new HasCycleTester( c)
    } should be ( true)
  }
}