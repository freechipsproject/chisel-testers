/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

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

class FixedPointDivide(val fixedType: FixedPoint, val shiftAmount: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(fixedType)
    val out = Output(fixedType)
  })

  io.out := (io.in.asUInt >> shiftAmount).asFixedPoint(fixedType.binaryPoint)
}

class FixedPointDivideTester(c: FixedPointDivide) extends PeekPokeTester(c) {
  for(d <- 0.0 to 15.0 by (1.0 / 3.0)) {
    pokeFixedPoint(c.io.in, d)


    step(1)

    println(s"$d >> 2 => ${peekFixedPoint(c.io.out)}")
    expectFixedPoint(c.io.out, d / 4.0, s"${c.io.out.name} got ${peekFixedPoint(c.io.out)} expected ${d / 4.0}")
  }
}

class FixedPointSpec extends FreeSpec with Matchers {
  "fixed point reduce work" in {
    iotesters.Driver.execute(Array.empty[String], () => new FixedPointReduce(FixedPoint(64.W, 60.BP), 10)) { c =>
      new FixedPointReduceTester(c)
    } should be (true)
  }

  "with enough bits fixed point pseudo divide should work" in {
    iotesters.Driver.execute(Array.empty[String], () => new FixedPointDivide(FixedPoint(64.W, 32.BP), 2)) { c =>
      new FixedPointDivideTester(c)
    } should be (true)
  }
  "not enough bits and fixed point pseudo divide will not work" in {
    iotesters.Driver.execute(Array.empty[String], () => new FixedPointDivide(FixedPoint(10.W, 4.BP), 2)) { c =>
      new FixedPointDivideTester(c)
    } should be (false)
  }
}
