package verilator

// NB! Yes: we *do* want to test the Chisel._ compatibility layer, so DO NOT change to
// chisel3._ here!
import Chisel._

class thingamabob() extends Module {
  val io = new Bundle {
    val address = Vec(32, UInt(INPUT, 16)).asInput
    val thingy = UInt(INPUT, 4)

    // FIXME delete any of the unused things below and the Verilator compile problem goes away
    val foo = UInt(INPUT, 1)
    val foo2 = UInt(OUTPUT, 8)
    val foo3 = Bool(OUTPUT)
    val foo4 = UInt(OUTPUT, 16)
    val foo5 = Bool(INPUT)
  }
  
  val blah = Reg(UInt(0, 4.W))
  blah := ((Mem(1 << 4, UInt(4.W)))(io.thingy))

  val bar = Cat(io.address(Cat(blah(3, 1), UInt(1, 1))),
      io.address(Cat(blah(3, 1), UInt(0, 1))))
}
