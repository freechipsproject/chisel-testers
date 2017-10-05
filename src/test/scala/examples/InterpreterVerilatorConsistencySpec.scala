// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._

class ExampleModule extends ImplicitInvalidateModule {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Bits(16.W)))
    val out = Decoupled(Bits(16.W))
  })

  val delay_value = 5.U

  val busy = RegInit(false.B)
  val in_reg   = RegInit(0.U(16.W))
  io.in.ready := !busy

  when(io.in.valid && !busy) {
    in_reg := io.in.bits
    busy := true.B
  }

  val wait_counter = RegInit(0.U(16.W))

  when(io.in.valid && !busy) {
    wait_counter := 0.U
  }

  when(busy) {
    when(wait_counter === delay_value) {
      io.out.bits := in_reg
    }.otherwise {
      io.out.bits := 0.U
      wait_counter := wait_counter + 1.U
    }
  }.otherwise {
    io.out.bits := 0.U
  }

  io.out.valid := (io.out.bits === in_reg) && (wait_counter === delay_value) && busy

  printf("From printf -- in: ready %d   valid %d   value %d  -- out:  ready %d  valid %d  value %d\n",
    io.in.ready, io.in.valid, io.in.bits,
    io.out.ready, io.out.valid, io.out.bits)
}

class ExampleTester(dut: ExampleModule) extends AdvTester(dut){
  reset(10)
  wire_poke(dut.io.in.valid, 0)
  wire_poke(dut.io.in.bits, 0)
  step(1)
  wire_poke( dut.io.in.valid, 1)
  wire_poke( dut.io.in.bits, 20)

  val inreadySignal:BigInt = peek(dut.io.in.ready)
  println(s"From peek in.ready: $inreadySignal")
  step(1)
}

class InterpreterVerilatorConsistencySpec extends ChiselFlatSpec {
  "Example" should "show decoupled port controls correctly with interpreter" in {
    chisel3.iotesters.Driver(() => new ExampleModule,"firrtl"){ c =>
      new ExampleTester(c)
    }should be(true)
  }

  "Example" should "show decoupled port controls correctly" in {
    chisel3.iotesters.Driver(() => new ExampleModule,"verilator"){ c =>
      new ExampleTester(c)
    }should be(true)
  }
}
