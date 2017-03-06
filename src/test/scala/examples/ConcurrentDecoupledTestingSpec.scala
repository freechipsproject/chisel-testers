// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import org.scalatest.{FreeSpec, Matchers}

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
  * This module can calculate n independent gcd results in parallel
  * @param numberOfEngines the number of parallel gcd engines in circuit
  */
class MultiGcdCalculator(val numberOfEngines: Int) extends Module {
  val io  = IO(new Bundle {
    val input  = Flipped(Vec(numberOfEngines, Decoupled(new GcdInput())))
    val output = Vec(numberOfEngines, Valid(new GcdOutput))
  })

  /**
    * This function saves the inputs and computes the gcd of the inputs and returns all three values
    * @param decoupledInput gcd operands
    * @param validOutput    gcd result, i.e. operands with the gcd of them
    */
  def gcdLogic(decoupledInput: DecoupledIO[GcdInput], validOutput: Valid[GcdOutput]): Unit = {
    val x = Reg(UInt(RealGCD3.num_width.W))
    val y = Reg(UInt(RealGCD3.num_width.W))
    val xOriginal = Reg(UInt(RealGCD3.num_width.W))
    val yOriginal = Reg(UInt(RealGCD3.num_width.W))
    val p = RegInit(false.B)

    val ti = RegInit(0.U(RealGCD3.num_width.W))
    ti := ti + 1.U

    decoupledInput.ready := !p

    when(decoupledInput.valid && !p) {
      x := decoupledInput.bits.a
      y := decoupledInput.bits.b
      xOriginal := decoupledInput.bits.a
      yOriginal := decoupledInput.bits.b
      p := true.B
    }

    when(p) {
      when(x > y) {
        x := y       // flip register values
        y := x
      }
      .otherwise {
        y := y - x
      }
    }

    validOutput.bits.gcd := x
    validOutput.bits.a   := xOriginal
    validOutput.bits.b   := yOriginal
    validOutput.valid := y === 0.U && p
    when(validOutput.valid) {
      p := false.B
    }
  }

  for(i <- 0 until numberOfEngines) {
    gcdLogic(io.input(i), io.output(i))
  }
}

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
  var valuesInputCount: Int = 0

  def addInput(driverNumber: Int, driver: DecoupledSource[GcdInput, TestGCD3Values]): Unit = {
    val inputData = pairsToProcess.dequeue()
    driver.inputs.enqueue(inputData)
    valuesInputCount += 1
     println(s"Input: $driverNumber $inputData count $valuesInputCount")
  }

  private val pairsToProcess = mutable.Queue.fill(numberOfSamples) {
    TestGCD3Values(rnd.nextInt(numberOfSamples) + 1, rnd.nextInt(numberOfSamples) + 1)
  }

  // give everybody something to do
  for((driver, index) <- inputHandlers.zipWithIndex) {
    addInput(index, driver)
  }

  def checkOutput(): Unit = {
    def ok(result: GcdResult): String = {
      val (e, _) = GCDCalculator.computeGcdResultsAndCycles(result.x, result.y)
      if(e == result.gcd) "ok" else s"FAILED, got ${result.gcd} should be $e"
    }
    outputHandlers.zipWithIndex.foreach { case (handler, index) =>
      if(handler.outputs.nonEmpty) {
        resultCount += 1
        val result = handler.outputs.dequeue()
        println(f"handler $index%3d event $resultCount%5d got output $result ${ok(result)}")
        if (pairsToProcess.nonEmpty) {
          addInput(index, inputHandlers(index))
        }
      }
    }
    takestep()
  }

  //noinspection LoopVariableNotUpdated
  while(resultCount < numberOfSamples) {
    checkOutput()
  }
}

object ConcurrentDecoupledTestingSpec {
  val parallelEngines = 4
  val numberOfSamples = 100
}

class ConcurrentDecoupledTestingSpec extends FreeSpec with Matchers {
  "This demonstrates waiting on two independent decoupled interfaces" in {
    chisel3.iotesters.Driver.execute(
      Array("--backend-name", "firrtl"),
      () => new MultiGcdCalculator(ConcurrentDecoupledTestingSpec.parallelEngines)
    ) { c =>
      new MultiGcdCalculatorTester(c)
     } should be(true)
  }
}

