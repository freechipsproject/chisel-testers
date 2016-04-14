// See LICENSE for license details.

package examples

// See LICENSE for license details.

package examples

import Chisel._
import Chisel.hwiotesters.{ClassicTester, SteppedHWIOTester, ChiselFlatSpec}

class ClassicGCD extends Module {
  val int_width = 16
  val io = new Bundle {
    val a  = UInt(INPUT,  width = int_width)
    val b  = UInt(INPUT,  width = int_width)
    val e  = Bool(INPUT)
    val z  = UInt(OUTPUT, width = int_width)
    val v  = Bool(OUTPUT)
  }
  val x  = Reg(UInt(width = int_width))
  val y  = Reg(UInt(width = int_width))
  when   (x > y) { x := x - y }
  unless (x > y) { y := y - x }
  when (io.e) { x := io.a; y := io.b }
  io.z := x
  io.v := y === UInt(0)
}

class ClassicGCDUnitTester(c: ClassicGCD) extends ClassicTester(c) {
  def computeGcd(a: Int, b: Int): (Int, Int) = {
    var x = a
    var y = b
    var depth = 1
    while(y > 0 ) {
      if (x > y) {
        x -= y
      }
      else {
        y -= x
      }
      depth += 1
    }
    (x, depth)
  }

  val (a, b, z) = (64, 48, 16)

  enable_all_debug = true

  poke(c.io.a, a)
  poke(c.io.b, b)
  poke(c.io.e, 1)
  step(1)
  poke(c.io.e, 0)

  val (expected_gcd, steps) = computeGcd(a, b)

  step(steps - 1) // -1 is because we step(1) already to toggle the enable
  expect(c.io.z, expected_gcd)
  expect(c.io.v, 1)
}

class ClassicGCDTester extends ChiselFlatSpec {
  "a" should "b" in {
    assertTesterPasses {
      val device_under_test = Module(new ClassicGCD)
      new ClassicGCDUnitTester(device_under_test)
    }
  }
}
