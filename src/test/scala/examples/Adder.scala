// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.{ChiselFlatSpec, Exerciser, PeekPokeTester, SteppedHWIOTester}
import org.scalatest.{FreeSpec, Matchers}

class Adder(val w: Int) extends Module {
  val io = IO(new Bundle {
    val in0 = Input(UInt(w.W))
    val in1 = Input(UInt(w.W))
    val out = Output(UInt(w.W))
  })
// printf("in0 %d in1 %d result %d\n", io.in0, io.in1, io.out)
  io.out := io.in0 + io.in1
}

class AdderTests extends SteppedHWIOTester {
  val device_under_test = Module( new Adder(10) )
  val c = device_under_test
  enable_all_debug = true

  rnd.setSeed(0L)
  for (i <- 0 until 10) {
    val in0 = rnd.nextInt(1 << c.w)
    val in1 = rnd.nextInt(1 << c.w)
    poke(c.io.in0, in0)
    poke(c.io.in1, in1)
    expect(c.io.out, (in0 + in1) & ((1 << c.w) - 1))

    step(1)
  }
}

class AdderExerciser extends Exerciser {
  val width = 32
  val (x_range_start, y_range_start) = (3, 7)
  val device_under_test = Module( new Adder(internal_counter_width) )
  val c = device_under_test

  printf(s"state_number %d, ticker %d, state_locked %x max_ticks %d",
    state_number, ticker, state_locked, max_ticks_for_state)

  def range(start:Int): Range = {
    val count = 20 // this forces ranges to all be the same size
    Range(start, start + count)
  }
  val in0_vec = VecInit(range(x_range_start).map(_.asUInt))
  val in1_vec = VecInit(range(y_range_start).map(_.asUInt))

  val expected_out_vec = VecInit(in0_vec.zip(in1_vec).map { case (i,j) => i + j })
  val test_number      = RegInit(0.U(internal_counter_width.W))

  buildState("check adder")(StopCondition(test_number > (range(0).size).asUInt)) { () =>
    printf(
      "%d ticker %d test# %d : %d + %d => %d expected %d\n",
      state_number, ticker, test_number,
      in0_vec(test_number), in1_vec(test_number),
      in0_vec(test_number) + in1_vec(test_number),
      expected_out_vec(test_number)
    )
    assert(expected_out_vec(test_number) === in0_vec(test_number) + in1_vec(test_number))
    test_number := test_number + 1.U
  }
  finish()
}

class AdderGo extends ChiselFlatSpec {
  "adder" should "add things properly" in {
    assertTesterPasses { new AdderExerciser }
  }
}

class AdderTester extends ChiselFlatSpec {
  "Adder" should "compile and run without incident" in {
    assertTesterPasses { new AdderTests }
  }
}

class SignedAdder(val w: Int) extends Module {
  val io = IO(new Bundle {
    val in0 = Input(SInt(w.W))
    val in1 = Input(SInt(w.W))
    val out = Output(SInt(w.W))
  })
  // printf("in0 %d in1 %d result %d\n", io.in0, io.in1, io.out)
  io.out := io.in0 + io.in1
}

class SignedAdderTester(c: SignedAdder) extends PeekPokeTester(c) {
  for {
    i <- -10 to 10
    j <- -10 to 10
  } {
    poke(c.io.in0, i)
    poke(c.io.in1, j)
    step(1)
    println(s"signed adder $i + $j got ${peek(c.io.out)} should be ${i+j}")
    expect(c.io.out, i + j)
    step(1)
  }
}

class SignedAdderSpec extends FreeSpec with Matchers {
  "tester should returned signed values with interpreter" in {
    iotesters.Driver.execute(Array("--backend-name", "firrtl", "--target-dir", "test_run_dir"), () => new SignedAdder(16)) { c =>
      new SignedAdderTester(c)
    } should be (true)
  }

  "tester should returned signed values with verilator" in {
    iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"), () => new SignedAdder(16)) { c =>
      new SignedAdderTester(c)
    } should be (true)
  }
}

class FixedPointAdder(val w: Int) extends Module {
  val io = IO(new Bundle {
    val in0 = Input(FixedPoint(16.W, 2.BP))
    val in1 = Input(FixedPoint(16.W, 2.BP))
    val out = Output(FixedPoint(16.W, 2.BP))
  })
  // printf("in0 %d in1 %d result %d\n", io.in0, io.in1, io.out)
  io.out := io.in0 + io.in1
}

class FixedPointAdderTester(c: FixedPointAdder) extends PeekPokeTester(c) {
  for {
//    i <- -10 to 10
//    j <- -10 to 10
    i <- -10 to -9
    j <- -10 to -8
  } {
    poke(c.io.in0, i)
    poke(c.io.in1, j)
    step(1)
    println(s"signed adder $i + $j got ${peek(c.io.out)} should be ${i+j}")
    expect(c.io.out, i + j)
    step(1)
  }

}

class FixedPointAdderSpec extends FreeSpec with Matchers {
  "tester should returned signed values with interpreter" in {
    iotesters.Driver.execute(Array("--backend-name", "firrtl", "--target-dir", "test_run_dir"), () => new FixedPointAdder(16)) { c =>
      new FixedPointAdderTester(c)
    } should be (true)
  }

  //TODO: make this work
  "tester should returned signed values" ignore {
    iotesters.Driver.execute(Array("--backend-name", "verilator", "--target-dir", "test_run_dir"), () => new FixedPointAdder(16)) { c =>
      new FixedPointAdderTester(c)
    } should be (true)
  }
}

