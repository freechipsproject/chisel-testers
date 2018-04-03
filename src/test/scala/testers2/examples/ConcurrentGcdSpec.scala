// See LICENSE for license details.

package testers2.examples

import chisel3._
import chisel3.tester._
import chisel3.tester.TestAdapters._
import chisel3.util.{Decoupled, DecoupledIO, Valid}
import examples.{ConcurrentDecoupledTestingSpec, GCDCalculator, RealGCD3}
import org.scalatest.{FreeSpec, Matchers}

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

case class GcdTriplet(a: Int, b: Int, gcd: Int)

class ConcurrentGcdSpec extends FreeSpec with ChiselScalatestTester with Matchers {

  "it should run really nicely" in {
    test(new MultiGcdCalculator(ConcurrentDecoupledTestingSpec.parallelEngines)) { c =>
      val triplets = (1 to 10).flatMap { a =>
        (1 to 10).map {
          b => GcdTriplet(a, b, GCDCalculator.computeGcdResultsAndCycles(a, b)._1)
        }
      }
      println(triplets.toList)

      class TripletQueue(triplets: Seq[GcdTriplet]) {
        var counter = 0
        val total = triplets.length

        def get(): Option[GcdTriplet] = {
          synchronized {
            if (counter >= total) {
              None
            }
            else {
              val result = counter
              counter += 1
              Some(triplets(result))
            }
          }
        }
      }

      val tripletQueue = new TripletQueue(triplets)

      def doOneGcd(engineNumber: Int)(): Unit = {
        tripletQueue.get() match {
          case Some(pair) =>
            var waitCount = 0
            c.io.input(engineNumber).bits.a.poke(pair.a.U)
            c.io.input(engineNumber).bits.b.poke(pair.b.U)
            c.io.input(engineNumber).valid.poke(true.B)
            c.clock.step(1)
            c.io.input(engineNumber).ready.poke(true.B)

            while(c.io.output(engineNumber).valid.peek().litValue() == BigInt(0)) {
              c.clock.step(1)
              waitCount += 1
              if(waitCount > 1000) throw new Exception(s"Engine $engineNumber bad wait count $waitCount")
            }

            println(
              s"  Engine $engineNumber: GCD(${pair.a}, ${pair.b}) " +
                      s"-> ${c.io.output(engineNumber).bits.gcd.peek().litValue()}"
            )

            c.io.output(engineNumber).bits.gcd.expect(pair.gcd.U)
            doOneGcd(engineNumber)()
          case _ =>
        }
      }

      val firstHandler = fork(doOneGcd(0))
      var handler = firstHandler
      for(handlerNumber <- 1 until ConcurrentDecoupledTestingSpec.parallelEngines) {
        handler = handler.fork(doOneGcd(handlerNumber))
      }
      handler.join()
    }
  }
}

