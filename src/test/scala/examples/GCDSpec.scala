// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import org.scalatest.{Matchers, FlatSpec}

object RealGCD2 {
  val num_width = 16

  /**
    * This is an example of how to launch the repl with the RealGCD2 module
    * @param args command line arguments
    */
//  def main(args: Array[String]) {
  //    val optionsManager = new ReplOptionsManager
  //    if(optionsManager.parse(args)) {
  //      iotesters.Driver.executeFirrtlRepl(() => new RealGCD2, optionsManager)
  //    }
  //  }
}

class RealGCD2Input extends Bundle {
  private val theWidth = RealGCD2.num_width
  val a = UInt(theWidth.W)
  val b = UInt(theWidth.W)
}

class RealGCD2 extends Module {
  private val theWidth = RealGCD2.num_width
  val io  = IO(new Bundle {
    // we use quirky names here to test fixed bug in verilator backend
    val RealGCD2in  = Flipped(Decoupled(new RealGCD2Input()))
    val RealGCD2out = Valid(UInt(theWidth.W))
  })

  val x = Reg(UInt(theWidth.W))
  val y = Reg(UInt(theWidth.W))
  val p = RegInit(false.B)

  val ti = RegInit(0.U(theWidth.W))
  ti := ti + 1.U

  io.RealGCD2in.ready := !p

  when (io.RealGCD2in.valid && !p) {
    x := io.RealGCD2in.bits.a
    y := io.RealGCD2in.bits.b
    p := true.B
  }

  when (p) {
    when (x > y)  { x := y; y := x }
      .otherwise    { y := y - x }
  }

  io.RealGCD2out.bits  := x
  io.RealGCD2out.valid := y === 0.U && p
  when (io.RealGCD2out.valid) {
    p := false.B
  }
}

class GCDPeekPokeTester(c: RealGCD2) extends PeekPokeTester(c)  {
  for {
    i <- 1 to 10
    j <- 1 to 10
  } {
    val (gcd_value, _) = GCDCalculator.computeGcdResultsAndCycles(i, j)

    poke(c.io.RealGCD2in.bits.a, i)
    poke(c.io.RealGCD2in.bits.b, j)
    poke(c.io.RealGCD2in.valid, 1)

    var count = 0
    while(peek(c.io.RealGCD2out.valid) == BigInt(0) && count < 20) {
      step(1)
      count += 1
    }
    if(count > 30) {
      // println(s"Waited $count cycles on gcd inputs $i, $j, giving up")
      System.exit(0)
    }
    expect(c.io.RealGCD2out.bits, gcd_value)
    step(1)
  }
}

class GCDSpec extends FlatSpec with Matchers {
  behavior of "GCDSpec"

  it should "compute gcd excellently" in {
    iotesters.Driver.execute(() => new RealGCD2, new TesterOptionsManager) { c =>
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
      testerOptions = testerOptions.copy(backendName = "firrtl", testerSeed = 7L)
      interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
    }
    iotesters.Driver.execute(() => new RealGCD2, manager) { c =>
      new GCDPeekPokeTester(c)
    } should be (true)
  }
}

