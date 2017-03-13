package verilator

import chisel3._

/**
  * The practice of creating Seq's or Vec's of IO's is problematic
  */
class doohickey() extends Module {
  val io = IO(new Bundle {})
  //TODO: figure out if this idiom is the key point here or if some other example would suffice
  val bobs = Seq.fill(16) {
    Module(new thingamabob()).io
  }
}
