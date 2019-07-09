// See LICENSE for license details.

package verilator

import chisel3._
import chisel3.iotesters._

class GCD extends Module {
  val io = IO(new Bundle {
    val a  = Input(UInt(32.W))
    val b  = Input(UInt(32.W))
    val e  = Input(Bool())
    val z  = Output(UInt(32.W))
    val v  = Output(Bool())
  })
  val x = Reg(UInt(32.W))
  val y = Reg(UInt(32.W))
  when (x > y)   { x := x -% y }
  .otherwise     { y := y -% x }
  when (io.e) { x := io.a; y := io.b }
  io.z := x
  io.v := y === 0.U
}

class GCDTester(dut: GCD, ntests: Int = 10000) extends PeekPokeTester(dut) {
  def gcd(a: Int, b: Int): Int = {
     if(b ==0) {
       a
     } else {
       gcd(b, a % b)
     }
  }

  reset(5)

  val startTime = System.currentTimeMillis

  for (_ <- 0 until ntests) {
    val a = scala.util.Random.nextInt(Integer.MAX_VALUE)
    val b = scala.util.Random.nextInt(Integer.MAX_VALUE)
    poke(dut.io.e, 1)
    // expect(dut.io.e, 1)
    poke(dut.io.a, a)
    poke(dut.io.b, b)
    step(1)
    poke(dut.io.e, 0)
    while (peek(dut.io.v) == 0) {
      step(1)
    }
    expect(dut.io.z, gcd(a, b))
  }

  val endTime = System.currentTimeMillis

  println(s"Total sim time is ${ (endTime - startTime) / 1000.0 } seconds")
}
