// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
import org.scalatest.{Matchers, FlatSpec}

object RealGCD2 {
  val num_width = 16

  /**
    * This is an example of how to launch the repl with the RealGCD2 module
    * @param args command line arguments
    */
  def main(args: Array[String]) {
    val optionsManager = new ReplOptionsManager
    if(optionsManager.parse(args)) {
      iotesters.Driver.executeFirrtlRepl(() => new RealGCD2, optionsManager)
    }
  }
}

class RealGCD2Input extends Bundle {
  val theWidth = RealGCD2.num_width
  val a = UInt(theWidth.W)
  val b = UInt(theWidth.W)
}

class RealGCD2 extends Module {
  val theWidth = RealGCD2.num_width
  val io  = IO(new Bundle {
    val in  = Decoupled(new RealGCD2Input()).flip()
    val out = Valid(UInt(theWidth.W))
  })

  val x = Reg(UInt(theWidth.W))
  val y = Reg(UInt(theWidth.W))
  val p = Reg(init=false.B)

  val ti = Reg(init=0.U(theWidth.W))
  ti := ti + 1.U

  io.in.ready := !p

  when (io.in.valid && !p) {
    x := io.in.bits.a
    y := io.in.bits.b
    p := true.B
  }

  when (p) {
    when (x > y)  { x := y; y := x }
      .otherwise    { y := y - x }
  }

  io.out.bits  := x
  io.out.valid := y === 0.U && p
  when (io.out.valid) {
    p := false.B
  }
}

class GCDPeekPokeTester(c: RealGCD2) extends PeekPokeTester(c)  {
  for {
    i <- 1 to 10
    j <- 1 to 10
  } {
    val (gcd_value, _) = GCDCalculator.computeGcdResultsAndCycles(i, j)

    poke(c.io.in.bits.a, i)
    poke(c.io.in.bits.b, j)
    poke(c.io.in.valid, 1)

    var count = 0
    while(peek(c.io.out.valid) == BigInt(0) && count < 20) {
      step(1)
      count += 1
    }
    if(count > 30) {
      // println(s"Waited $count cycles on gcd inputs $i, $j, giving up")
      System.exit(0)
    }
    expect(c.io.out.bits, gcd_value)
    step(1)
  }
}

class GCDSpec extends FlatSpec with Matchers {
  behavior of "GCDSpec"

  it should "compute gcd excellently" in {
    chisel3.iotesters.Driver(() => new RealGCD2) { c =>
      new GCDPeekPokeTester(c)
    } should be(true)
  }

  it should "run verilator via command line arguments" in {
    // val args = Array.empty[String]
    val args = Array("--backend-name", "verilator")
    iotesters.Driver.execute(args, () => new RealGCD2) { c =>
      new GCDPeekPokeTester(c)
    } should be (true)
  }
  it should "run firrtl via command line arguments" in {
    // val args = Array.empty[String]
    val args = Array("--backend-name", "firrtl", "--fint-write-vcd")
    iotesters.Driver.execute(args, () => new RealGCD2) { c =>
      new GCDPeekPokeTester(c)
    } should be (true)
  }

  it should "run firrtl via direct options configuration" in {
    val manager = new TesterOptionsManager {
      testerOptions = TesterOptions(backendName = "firrtl", testerSeed = 7L)
      interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
    }
    iotesters.Driver.execute(() => new RealGCD2, manager) { c =>
      new GCDPeekPokeTester(c)
    } should be (true)
  }
}

