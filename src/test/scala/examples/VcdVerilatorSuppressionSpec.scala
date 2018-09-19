// See LICENSE for license details.

package examples

import java.io.File

import chisel3._
import chisel3.iotesters.{Driver, PeekPokeTester, TesterOptionsManager}
import org.scalatest.{FreeSpec, Matchers}

class Returns42 extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  io.out := 42.U
}

class Returns42Tester(c: Returns42) extends PeekPokeTester(c) {
  step(1)
  expect(c.io.out, 42)
}

class Returns43 extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  io.out := 43.U
}

class Returns43Tester(c: Returns43) extends PeekPokeTester(c) {
  step(1)
  expect(c.io.out, 43)
}

/**
  * We use to differently named dut's because the default behavior is
  * to base the targetdir on the dut name
  */
class VcdVerilatorSuppressionSpec extends FreeSpec with Matchers {
  "Users can control generation of VCD when using verilator backend" - {
    "default is to generate a vcd" in {
      val optionsManager = new TesterOptionsManager() {
        testerOptions = testerOptions.copy(backendName = "verilator")
      }
      Driver.execute(() => new Returns42, optionsManager) { c =>
        new Returns42Tester(c)
      }
      val vcdFile = new File(optionsManager.getBuildFileName("vcd"))
      vcdFile.exists() should be (true)

    }
    "suppress the vcd output like this" in {
      val optionsManager = new TesterOptionsManager() {
        testerOptions = testerOptions.copy(backendName = "verilator", suppressVerilatorVcd = true)
      }
      Driver.execute(() => new Returns43, optionsManager) { c =>
        new Returns43Tester(c)
      }

      val vcdFile = new File(optionsManager.getBuildFileName("vcd"))
      vcdFile.exists() should be (false)
    }
  }
}
