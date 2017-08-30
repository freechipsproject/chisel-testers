// See LICENSE for license details.

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FreeSpec, Matchers}

class FixedPointReduce(val fixedType: FixedPoint, val size: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(size, fixedType))
    val sum = Output(fixedType)
  })

  io.sum := io.in.reduce(_ + _)
}

class FixedPointReduceTester(c: FixedPointReduce) extends PeekPokeTester(c) {
  private val nums = (0 until c.size).map { _ => 0.1 }

  println(s"nums ${nums.mkString(", ")}")

  nums.zipWithIndex.foreach { case (num, index) =>
    pokeFixedPoint(c.io.in(index), num)
  }

  step(1)

  println(s"peek got ${peekFixedPoint(c.io.sum)}")
}

class FixedPointSpec extends FreeSpec with Matchers {
  "fixed point should work" in {
    iotesters.Driver.execute(Array.empty[String], () => new FixedPointReduce(FixedPoint(64.W, 60.BP), 10)) { c =>
      new FixedPointReduceTester(c)
    }
  }
}
