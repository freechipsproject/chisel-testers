// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
import org.scalatest.{Matchers, FlatSpec}

object RealGCD3 {
  val num_width = 16
  def computeGcdResultsAndCycles(a: Int, b: Int, depth: Int = 1): (Int, Int) = {
    if(b == 0) {
      (a, depth)
    }
    else {
      if (a > b) {
        computeGcdResultsAndCycles(b, a, depth + 1)
      } else {
        computeGcdResultsAndCycles(a, b - a, depth+1 )
      }
    }
  }
}

class RealGCD3Input extends Bundle {
  val a = Bits(width = RealGCD3.num_width)
  val b = Bits(width = RealGCD3.num_width)
}

case class TestGCD3Values(a: BigInt, b: BigInt)

class RealGCD3 extends Module {
  val io  = IO(new Bundle {
    val in  = Decoupled(new RealGCD3Input()).flip()
    val out = Valid(UInt(width = RealGCD3.num_width))
  })

  val x = Reg(UInt(width = RealGCD3.num_width))
  val y = Reg(UInt(width = RealGCD3.num_width))
  val p = Reg(init=Bool(false))

  val ti = Reg(init=UInt(0, width = RealGCD3.num_width))
  ti := ti + UInt(1)

  io.in.ready := !p

  when (io.in.valid && !p) {
    x := io.in.bits.a
    y := io.in.bits.b
    p := Bool(true)
  }

  when (p) {
    when (x > y)  { x := y; y := x }
      .otherwise    { y := y - x }
    printf("x: %d, y: %d\n", x, y)
  }.otherwise { printf("stalled\n")}

  //  printf("ti %d  x %d y %d  in_ready %d  in_valid %d  out %d out_valid %d==============\n",
  //    ti, x, y, io.in.ready, io.in.valid, io.out.bits, io.out.valid)
  //      ti, x, y, io.in.ready, io.in.valid, io.out.bits, io.out.ready, io.out.valid)

  io.out.bits  := x
  io.out.valid := y === Bits(0) && p
  when (io.out.valid) {
    p := Bool(false)
  }
}

class GCDAdvTester(c: RealGCD3) extends AdvTester(c)  {
  for {
    i <- 1 to 10
    j <- 1 to 10
  } {
    val (gcd_value, nCycles) = RealGCD3.computeGcdResultsAndCycles(i, j)
    val gcdOutputHandler = new ValidSink(c.io.out, (outPort: UInt) => {
//      println(s"cycles: $cycles")
      peek(outPort)
    })
    val gcdInputDriver = new DecoupledSource(c.io.in, (inPorts: RealGCD3Input, inValues: TestGCD3Values) => {
      wire_poke(inPorts.a, inValues.a)
      wire_poke(inPorts.b, inValues.b)
    })

    gcdInputDriver.inputs.enqueue(TestGCD3Values(i, j))
    gcdInputDriver.process()
    eventually(gcdOutputHandler.outputs.size != 0, nCycles + 2)
    val result = gcdOutputHandler.outputs.dequeue()
    assert(result == gcd_value)
  }
}

class AdvTesterSpec extends FlatSpec with Matchers {
  behavior of "GCDAdvTester"

  it should "compute gcd excellently" in {
    chisel3.iotesters.Driver(() => new RealGCD3) { c =>
      new GCDAdvTester(c)
     } should be(true)
  }

  it should "run verilator via command line arguments" in {
    // val args = Array.empty[String]
    val args = Array("--backend-name", "verilator")
    iotesters.Driver.execute(args, () => new RealGCD3) { c =>
      new GCDAdvTester(c)
    } should be (true)
  }
  it should "run firrtl via command line arguments" in {
    // val args = Array.empty[String]
    val args = Array("--backend-name", "firrtl", "--fint-write-vcd")
    iotesters.Driver.execute(args, () => new RealGCD3) { c =>
      new GCDAdvTester(c)
    } should be (true)
  }

  it should "run firrtl via direct options configuration" in {
    // val args = Array.empty[String]
    val manager = new TesterOptionsManager {
      testerOptions = TesterOptions(backendName = "firrtl")
      interpreterOptions = InterpreterOptions(writeVCD = true)
    }
    val args = Array("--backend-name", "firrtl", "--fint-write-vcd")
    iotesters.Driver.execute(args, () => new RealGCD3) { c =>
      new GCDAdvTester(c)
    } should be (true)
  }
}

