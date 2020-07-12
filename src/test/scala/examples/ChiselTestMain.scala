package examples

import java.nio.file.{Paths, Files}

import chisel3._
import chisel3.iotesters._
import org.scalatest.{FreeSpec, Matchers}

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
object ChiselMainTest extends App {

  val testDir = Paths.get("test_run_dir", "test")

  if (Files.notExists(testDir)) Files.createDirectories(testDir)

  iotesters.chiselMainTest(
    Array(
      "--targetDir", testDir.toString,
      "--verilator",
      "--v",
      "--genHarness",
      "--compile",
      "--test"), () => new TestModule) {
    c => new PeekPokeTester(c) {
      poke(c.io.in, true)
      expect(c.io.out, true)
    }
  }
}