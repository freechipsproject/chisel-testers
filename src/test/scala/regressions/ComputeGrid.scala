// See LICENSE for license details.

package regressions

import chisel3._
import chisel3.iotesters.PeekPokeTester
import treadle.chronometry.Timer

import scala.collection.mutable

object Computer {
  trait PrimOp {
    def apply(a: UInt, b: UInt): UInt
  }

  case object Add extends PrimOp {
    def apply(a: UInt, b: UInt): UInt = a +% b
  }
  case object Mul extends PrimOp {
    def apply(a: UInt, b: UInt): UInt = a * b
  }
}

class ComputeRow(
  elementCount               : Int, bitWidth: Int,
  function1                  : (UInt, UInt) => UInt,
  function2                  : (UInt, UInt) => UInt,
  useRegisters               : Boolean = true
) extends Module {

  //noinspection TypeAnnotation
  val io = IO(new Bundle {
    val inputs  = Input(Vec(elementCount, UInt(bitWidth.W)))
    val outputs = Output(Vec(elementCount, UInt(bitWidth.W)))
  })

  for(List(i: Int, j: Int) <- (0 until elementCount).toList.grouped(2)) {
    if(useRegisters) {
      val reg1 = RegNext(function1(io.inputs(i.U), io.inputs(j)))
      val reg2 = RegNext(function2(io.inputs(i.U), io.inputs(j)))
      io.outputs(i) := reg1
      io.outputs(j) := reg2
    }
    else {
      io.outputs(i) := function1(io.inputs(i.U), io.inputs(j))
      io.outputs(j) := function2(io.inputs(i.U), io.inputs(j))
    }
  }
}

//noinspection TypeAnnotation
class ComputeGrid(
  val elementCount: Int,
  val rowCount        : Int,
  bitWidth        : Int,
  function1       : (UInt, UInt) => UInt,
  function2       : (UInt, UInt) => UInt,
  useRegisters    : Boolean = true
) extends Module {

  val io = IO(new Bundle {
    val inputs  = Input(Vec(elementCount, UInt(bitWidth.W)))
    val outputs = Output(Vec(elementCount, UInt(bitWidth.W)))
    val init    = Input(Bool())
  })

  private val lastRow = (0 until rowCount).foldLeft(io.inputs) { (ins, _) =>
    val computeRow = Module(new ComputeRow(elementCount, bitWidth, function1, function2))
    computeRow.io.inputs := ins
    computeRow.io.outputs
  }

  io.outputs := lastRow
}

class ComputerTester(timer: Timer, c: ComputeGrid) extends PeekPokeTester(c) {
  timer("runtime") {
    (0 until c.elementCount).foreach { i =>
      poke(c.io.inputs(i), i)
    }

    for (_ <- 0 until c.rowCount + 20) {
      step(1)
    }
    // println(s"${(0 until c.elementCount).map { i => peek(c.io.outputs(i)) }.map(x => f"$x%10d").mkString(", ")}")
  }
}

object ComputerRegression {
  case class RunConfiguration(
    gridColumns      : Int,
    gridRows         : Int,
    bitWidth         : Int,
    function         : Computer.PrimOp,
    useRegisters     : Boolean = true
  ) {

    override def toString: String = {
      val x = s"${gridColumns}X$gridRows"
      f" $x%8s bits $bitWidth function $function use registers $useRegisters"
    }

    def asDirectoryName: String = {
      s"compute$gridColumns-$gridRows-$bitWidth-$function-$useRegisters"
    }
  }

  def main(args: Array[String]): Unit = {

    val configurations = Seq(
      RunConfiguration(40, 40, 30, Computer.Add),
      RunConfiguration(40, 40, 30, Computer.Mul),
      RunConfiguration(40, 40, 30, Computer.Add, useRegisters = false),
      RunConfiguration(40, 40, 30, Computer.Mul, useRegisters = false),

      RunConfiguration(40, 40, 60, Computer.Add),
      RunConfiguration(40, 40, 60, Computer.Mul),
      RunConfiguration(40, 40, 200, Computer.Add),
      RunConfiguration(40, 40, 128, Computer.Mul)
    )
//    val backends = Array("firrtl", "treadle", "verilator")
    val backends = Array("treadle", "verilator")
    val timers = new mutable.HashMap[String, Timer]

    for(configuration <- configurations) {
      for (backendName <- backends) {
        val timer: Timer = new Timer()
        timers(backendName) = timer
        timer.enabled = true

        iotesters.Driver.timedExecute(
          Array(
            "--backend-name", backendName,
            "--target-dir", s"test_run_dir/${configuration.asDirectoryName}",
            "--top-name", "work"
          ),
          () => new ComputeGrid(
            elementCount = configuration.gridColumns,
            rowCount     = configuration.gridRows,
            bitWidth     = configuration.bitWidth,
            function1    = configuration.function.apply,
            function2    = configuration.function.apply,
            useRegisters = configuration.useRegisters
          ),
          timer) { c =>
          (0 until 20).foldLeft(new ComputerTester(timer, c)) { (_, _) =>
            new ComputerTester(timer, c)
          }
        }
      }

      println("=" * 100)
      println(s"Configuration $configuration")
      for (backendName <- backends) {
        val timer = timers(backendName)
        println(s"$backendName\n${timer.report()}")
      }
      println("-" * 100)
    }
  }
}