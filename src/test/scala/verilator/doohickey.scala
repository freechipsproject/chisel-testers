package verilator

import chisel3._

class doohickey() extends Module {
  val io = new Bundle {
  }
  val bobs = Vec.fill(16) {
    Module(new iterator()).io
  }
}
