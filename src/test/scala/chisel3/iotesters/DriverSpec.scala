// See LICENSE for license details.

package chisel3.iotesters

import org.scalatest.{Matchers, FreeSpec}

import chisel3._

class DriverTest extends Module {
  val io = new Bundle {
    val in = UInt(1).flip()
    val out = UInt(1)
  }
  io.out := io.in
}

class DriverTestTester(c: DriverTest) extends PeekPokeTester(c) {
  poke(c.io.in, 1)
  expect(c.io.out, 1)
}

class DriverSpec extends FreeSpec with Matchers {
  "tester driver should support a wide range of downstream toolchain options" - {
    "default options should not fail" in {
      chisel3.iotesters.Driver.execute(
        Array.empty[String],
        () => new DriverTest
      ) should be (true)
    }
    "bad arguments should fail" in {
      chisel3.iotesters.Driver.execute(
        Array("--i-am-a-bad-argument"),
        () => new DriverTest
      ) should be (false)
    }
  }
}
