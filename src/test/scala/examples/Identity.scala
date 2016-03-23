// See LICENSE for license details.

package examples

import Chisel.hwiotesters.{ChiselFlatSpec, SteppedHWIOTester}
import Chisel._
import Chisel.internal.firrtl.{LitArg, ULit}

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
  var my_litArg : Option[LitArg] = None

  def init(bool: Bool, uint: UInt): Unit = {
    i_bool := bool
    i_uint := uint
    my_litArg = Some(ULit(uint.litValue(), uint.width))
  }
  override def litArg: Option[LitArg] = my_litArg
}

class Identity extends Module {
  val size = 16
  val io = new Bundle {
//    val bool_in = Bool(INPUT)
//    val bool_out = Bool(OUTPUT)
//
//    val uint_in = UInt(INPUT, width =  size)
//    val uint_out = UInt(OUTPUT, width =  size)
//
    val bundle_in = (new IdentityBundle).flip()
    val bundle_out = new IdentityBundle
  }

//  io.bool_out := io.bool_in
//  io.uint_out := io.uint_in
  io.bundle_out := io.bundle_in
//  io.bundle_out.i_uint := io.bundle_in.i_uint * UInt(2)
//  io.bundle_out.i_bool := ! io.bundle_in.i_bool
}

class IdentityTests extends SteppedHWIOTester {
  val device_under_test = Module(new Identity)
  val c = device_under_test
  enable_all_debug = true

  for (i <- 0 to 64) {
//    poke(c.io.bool_in, Bool(i % 2 == 0))
//    expect(c.io.bool_out, Bool(i % 2 == 0))
//    poke(c.io.uint_in, UInt(i))
//    expect(c.io.uint_out, UInt(i))

    poke(c.io.bundle_in, IdentityBundle(Bool(i % 3 == 0), UInt(10+i)))
    expect(c.io.bundle_out, IdentityBundle(Bool(i % 3 == 0), UInt(10+i)))
    step(1)
  }
}

class IdentityTester extends ChiselFlatSpec {
  "Identity" should "compile and run without incident" in {
    assertTesterPasses { new IdentityTests }
  }
}
