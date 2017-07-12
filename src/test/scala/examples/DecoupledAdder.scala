// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, OrderedDecoupledHWIOTester}
import chisel3.iotesters.{ImplicitInvalidateModule, implicitInvalidateOptions}

/**
  * Implements an adder that used decoupledIO for both input and output
  * This adder has a manual delay that is 10 cycles per add, just
  * to exercise the ready valid stuff
  */
class SlowDecoupledAdderIn extends Bundle {
  val a = UInt(16.W)
  val b = UInt(16.W)
}

class SlowDecoupledAdderOut extends Bundle {
  val c = Output(UInt(16.W))
}

class SlowDecoupledAdder extends ImplicitInvalidateModule {
  val delay_value = 10
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new SlowDecoupledAdderIn))
    val out = Decoupled(new SlowDecoupledAdderOut)
  })
  val busy    = RegInit(false.B)
  val a_reg   = RegInit(0.U(16.W))
  val b_reg   = RegInit(0.U(16.W))
  val wait_counter = RegInit(0.U(16.W))

  io.in.ready := !busy

  printf("in: ready %d   valid %d   a %d b %d   -- out:  ready %d  valid %d  c %d",
         io.in.ready, io.in.valid, io.in.bits.a, io.in.bits.b,
         io.out.ready, io.out.valid, io.out.bits.c)

  when(io.in.valid && !busy) {
    a_reg        := io.in.bits.a
    b_reg        := io.in.bits.b
    busy         := true.B
    wait_counter := 0.U
  }
  when(busy) {
    when(wait_counter > delay_value.asUInt) {
      io.out.bits.c := a_reg + b_reg
    }.otherwise {
      wait_counter := wait_counter + 1.U
    }
  }

  io.out.valid := (io.out.bits.c === a_reg + b_reg ) && busy

  when(io.out.valid) {
    busy          := false.B
  }
}

class DecoupledAdderTests extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new SlowDecoupledAdder())

  for {
    x <- 0 to 4
    y <- 0 to 6 by 2
    z = x + y
  } {
    inputEvent(device_under_test.io.in.bits.a -> x, device_under_test.io.in.bits.b -> y)
    outputEvent(device_under_test.io.out.bits.c -> z)
  }
}


class DecoupledAdderTester extends ChiselFlatSpec {
  "decoupled adder" should "add a bunch numbers slowly but correctly" in {
    assertTesterPasses { new DecoupledAdderTests }
  }
}



