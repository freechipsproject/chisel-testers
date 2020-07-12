package examples

import chisel3._
import chisel3.iotesters._

/**
  * Test module for chiselMainTest and chiselMain.apply with test
  */
class TestModule extends Module {
  val io = IO(new Bundle {
    val in = Input(Bool())
    val out = Output(Bool())
  })

  io.out := io.in
}

/**
  * Example of iotesters.chiselMainTest
  */
object chiselMainTest extends App {

  iotesters.chiselMainTest(
    Array(
      "--targetDir", "test_run_dir/test",
      "--verilator",
      //"--v",
      //"--genHarness",
      //"--compile",
      "--test"), () => new TestModule) {
    c => new PeekPokeTester(c) {
      poke(c.io.in, true)
      expect(c.io.out, true)
    }
  }
}