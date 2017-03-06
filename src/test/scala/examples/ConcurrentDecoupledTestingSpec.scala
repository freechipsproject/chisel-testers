// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
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
  * This module can calculate two independent gcd results in parallel
  */
class DualGcdCalculator extends Module {
  val io  = IO(new Bundle {
    val in1  = Flipped(Decoupled(new GcdInput()))
    val out1 = Valid(new GcdOutput)
    val in2  = Flipped(Decoupled(new GcdInput()))
    val out2 = Valid(new GcdOutput)
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

  gcdLogic(io.in1, io.out1)
  gcdLogic(io.in2, io.out2)
}

class DualGcdCalculatorTester(c: DualGcdCalculator) extends AdvTester(c)  {
  val gcdOutputHandler1 = new ValidSink(c.io.out1, (outPort: GcdOutput) => {
    GcdResult(peek(outPort.a).toInt, peek(outPort.b).toInt, peek(outPort.gcd).toInt)
  })
  val gcdInputDriver1 = new DecoupledSource(c.io.in1, (inPorts: GcdInput, inValues: TestGCD3Values) => {
    wire_poke(inPorts.a, inValues.a)
    wire_poke(inPorts.b, inValues.b)
  })
  val gcdOutputHandler2 = new ValidSink(c.io.out2, (outPort: GcdOutput) => {
    GcdResult(peek(outPort.a).toInt, peek(outPort.b).toInt, peek(outPort.gcd).toInt)
  })
  val gcdInputDriver2 = new DecoupledSource(c.io.in2, (inPorts: GcdInput, inValues: TestGCD3Values) => {
    wire_poke(inPorts.a, inValues.a)
    wire_poke(inPorts.b, inValues.b)
  })

  var resultCount = 0
  var valuesInputCount: Int = 0

  def addInput(name: String, driver: DecoupledSource[GcdInput, TestGCD3Values]): Unit = {
    val inputData = pairsToProcess.dequeue()
    driver.inputs.enqueue(inputData)
    valuesInputCount += 1
    // println(s"Input: $name $inputData count $valuesInputCount")
  }

  private val pairsToProcess = mutable.Queue.fill(100) { TestGCD3Values(rnd.nextInt(100) + 1, rnd.nextInt(100) + 1)}

  // prime the engine
  addInput("1", gcdInputDriver1)
  addInput("2", gcdInputDriver2)

  while(pairsToProcess.nonEmpty) {
    checkOutput()
  }

  def checkOutput(): Unit = {
    def ok(result: GcdResult): String = {
      val (e, _) = GCDCalculator.computeGcdResultsAndCycles(result.x, result.y)
      if(e == result.gcd) "ok" else s"FAILED, got ${result.gcd} should be $e"
    }
    if(gcdOutputHandler1.outputs.nonEmpty) {
      resultCount += 1
      val result = gcdOutputHandler1.outputs.dequeue()
      println(s"handler1 event $resultCount got output $result ${ok(result)}")
      if(pairsToProcess.nonEmpty) {
        addInput("1", gcdInputDriver1)
      }
    }
    if(gcdOutputHandler2.outputs.nonEmpty) {
      resultCount += 1
      val result = gcdOutputHandler2.outputs.dequeue()
      println(s"handler2 event $resultCount got output $result ${ok(result)}")
      if(pairsToProcess.nonEmpty) {
        addInput("2", gcdInputDriver2)
      }
    }
    takestep()
  }

  //noinspection LoopVariableNotUpdated
  while(resultCount < 100) {
    checkOutput()
  }
}

class ConcurrentDecoupledTestingSpec extends FreeSpec with Matchers {
  "This demonstrates waiting on two independent decoupled interfaces" in {
    chisel3.iotesters.Driver(() => new DualGcdCalculator) { c =>
      new DualGcdCalculatorTester(c)
     } should be(true)
  }
}

