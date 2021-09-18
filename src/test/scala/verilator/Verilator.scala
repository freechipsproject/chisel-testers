// SPDX-License-Identifier: Apache-2.0

package verilator


import chisel3.iotesters.chiselMain
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VerilatorTest extends AnyFlatSpec with Matchers {
  // Ensure we run this test in a clean directory to avoid stale files such as black_box_verilog_files.f
  //  See issue #132 - https://github.com/ucb-bar/chisel-testers/issues/132
  //  and issue #504 - https://github.com/ucb-bar/firrtl/issues/504
  val targetDir = firrtl.util.BackendCompilationUtilities.createTestDirectory("ChiselMainVerilatorTest")
  "The Verilator backend" should "be able to compile the cpp code" in {
      val args = Array[String]("--v",
          "--backend",
          "verilator",
          "--compile",
          "--genHarness",
          "--minimumCompatibility", "3.0.0",
          "--targetDir", targetDir.getPath
      )
      chiselMain(args, () => new doohickey())
    }
  it should "be able to deal with zero-width wires" in {
      chisel3.iotesters.Driver.execute(Array("--backend-name", "verilator"), () => new ZeroWidthIOModule) {
          c => new ZeroWidthIOTester(c)
    }
  }
}
