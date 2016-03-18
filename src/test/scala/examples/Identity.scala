// See LICENSE for license details.

package examples

import Chisel.hwiotesters.{ChiselFlatSpec, SteppedHWIOTester}
import Chisel._

object IdentityBundle {
  def apply(bool: Bool, uint: UInt): IdentityBundle = {
    val ib = Wire(new IdentityBundle)
    ib.init(bool, uint)
    ib
  }
}
class IdentityBundle extends Bundle {
  val i_bool = Bool(OUTPUT)
  val i_uint = UInt(OUTPUT, width = 16)

  def init(bool: Bool, uint: UInt): Unit = {
    i_bool := bool
    i_uint := uint
  }
}

class Identity extends Module {
  val size = 16
  val io = new Bundle {
    val bool_in = Bool(INPUT)
    val bool_out = Bool(OUTPUT)

    val uint_in = UInt(INPUT, width =  size)
    val uint_out = UInt(OUTPUT, width =  size)

    val bundle_in = (new IdentityBundle).flip()
    val bundle_out = new IdentityBundle
  }

  io.bool_out := io.bool_in
  io.uint_out := io.uint_in
//  io.bundle_in <> io.bundle_out
  io.bundle_out <> io.bundle_in
}

class IdentityTests extends SteppedHWIOTester {
  val device_under_test = Module( new Identity )
  val c = device_under_test
  enable_all_debug = true

  poke(c.io.bool_in, Bool(true))
  expect(c.io.bool_out, Bool(true))
  poke(c.io.uint_in, UInt(5))
  expect(c.io.uint_out, UInt(5))

  poke(c.io.bundle_in, IdentityBundle(Bool(true), UInt(10)).toBits() )
  expect(c.io.bundle_out, IdentityBundle(Bool(true), UInt(10)).toBits() )
}

class IdentityTester extends ChiselFlatSpec {
  "Identity" should "compile and run without incident" in {
    assertTesterPasses { new IdentityTests }
  }
}
