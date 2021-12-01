// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class GcdInput extends Bundle {
  val a = UInt(RealGCD3.num_width.W)
  val b = UInt(RealGCD3.num_width.W)
}

class GcdOutput extends Bundle {
  val a = UInt(RealGCD3.num_width.W)
  val b = UInt(RealGCD3.num_width.W)
  val gcd = UInt(RealGCD3.num_width.W)
}

case class GcdResult(x: Int, y: Int, gcd: Int) {
  override def toString: String = f"gcd of $x%3d and $y%3d is $gcd%3d"
}

/**
  * computes the gcd of two UInt operands, using decoupled for input and valid for output
  * engine takes different number of cycles depending on the operands
  * returns the operands plus their gcd
  */
class GcdEngine extends Module {
  val io = IO(new Bundle {
    val decoupledInput = Flipped(DecoupledIO(new GcdInput))
    val validOutput = Valid(new GcdOutput)
  })

  val x = Reg(UInt(RealGCD3.num_width.W))
  val y = Reg(UInt(RealGCD3.num_width.W))
  val xOriginal = Reg(UInt(RealGCD3.num_width.W))
  val yOriginal = Reg(UInt(RealGCD3.num_width.W))
  val busy = RegInit(false.B)

  io.decoupledInput.ready := !busy

  when(io.decoupledInput.valid && !busy) {
    x := io.decoupledInput.bits.a
    y := io.decoupledInput.bits.b
    xOriginal := io.decoupledInput.bits.a
    yOriginal := io.decoupledInput.bits.b
    busy := true.B
  }

  when(busy) {
    when(x > y) {
      x := y       // flip register values
      y := x
    }
      .otherwise {
        y := y - x
      }
  }

  io.validOutput.bits.gcd := x
  io.validOutput.bits.a   := xOriginal
  io.validOutput.bits.b   := yOriginal
  io.validOutput.valid    := y === 0.U && busy
  when(io.validOutput.valid) {
    busy := false.B
  }
}

/**
  * This module can calculate n independent gcd results in parallel
  * @param numberOfEngines the number of parallel gcd engines in circuit
  */
class MultiGcdCalculator(val numberOfEngines: Int) extends Module {
  val io  = IO(new Bundle {
    val input  = Flipped(Vec(numberOfEngines, Decoupled(new GcdInput())))
    val output = Vec(numberOfEngines, Valid(new GcdOutput))
  })

  for(i <- 0 until numberOfEngines) {
    val engine = Module(new GcdEngine)
    engine.io.decoupledInput <> io.input(i)
    engine.io.validOutput <> io.output(i)
  }
}

/**
  * creates a queue of pairs for which to compute gcd
  * input handlers take the top of the queue and pass the pair to their
  * engine.  As each engine completes a computation the result is taken
  * and tested and a new pair is taken from the queue and is pushed into that engine.
  * Tests is complete when all pairs in queue have been processed.
  *
  * @param c  The device to test
  */
class MultiGcdCalculatorTester(c: MultiGcdCalculator) extends AdvTester(c)  {
  private val numberOfSamples = ConcurrentDecoupledTestingSpec.numberOfSamples

  private val outputHandlers = c.io.output.map { out =>
    new ValidSink(out, (outPort: GcdOutput) => {
      GcdResult(peek(outPort.a).toInt, peek(outPort.b).toInt, peek(outPort.gcd).toInt)
    })
  }
  private val inputHandlers = c.io.input.map { in =>
    new DecoupledSource(in, (inPorts: GcdInput, inValues: TestGCD3Values) => {
      wire_poke(inPorts.a, inValues.a)
      wire_poke(inPorts.b, inValues.b)
    })
  }

  var resultCount = 0

  def addInput(driverNumber: Int, driver: DecoupledSource[GcdInput, TestGCD3Values]): Unit = {
    val inputData = pairsToProcess.dequeue()
    driver.inputs.enqueue(inputData)
  }

  private val pairsToProcess = mutable.Queue.fill(numberOfSamples) {
    TestGCD3Values(rnd.nextInt(numberOfSamples) + 1, rnd.nextInt(numberOfSamples) + 1)
  }

  // give each engine something to do
  for((driver, index) <- inputHandlers.zipWithIndex) {
    addInput(index, driver)
  }

  while(resultCount < numberOfSamples) {
    outputHandlers.zipWithIndex.foreach { case (handler, index) =>
      if(handler.outputs.nonEmpty) {
        resultCount += 1
        val result = handler.outputs.dequeue()

        val (expectedGcd, _) = GCDCalculator.computeGcdResultsAndCycles(result.x, result.y)

        assert(expectedGcd == result.gcd,
          f"handler $index%3d event $resultCount%5d got output $result expected $expectedGcd FAILED")

        if (pairsToProcess.nonEmpty) {
          addInput(index, inputHandlers(index))
        }
      }
    }
    takestep()
  }
}

object ConcurrentDecoupledTestingSpec {
  val parallelEngines = 4
  val numberOfSamples = 100
}

class ConcurrentDecoupledTestingSpec extends AnyFreeSpec with Matchers {
  "This demonstrates waiting on multiple independent decoupled interfaces" - {
    "using verilator" in {
      chisel3.iotesters.Driver.execute(
        Array("--backend-name", "verilator"),
        () => new MultiGcdCalculator(ConcurrentDecoupledTestingSpec.parallelEngines)
      ) { c =>
        new MultiGcdCalculatorTester(c)
      } should be(true)
    }
  }
}

