// See LICENSE for license details.

package examples

import chisel3._
import chisel3.iotesters.{PeekPokeTester, TesterOptionsManager}
import org.scalatest.{FreeSpec, Matchers}

class InstrumentMe extends Module {
  val io = IO(new Bundle {
    val uIn = Input(UInt(8.W))
    val sIn = Input(SInt(8.W))

    val uOut = Output(UInt(8.W))
    val sOut = Output(SInt(8.W))
    val uOutLower = Output(UInt(8.W))
    val uOutUpper = Output(UInt(8.W))
    val sOutLower = Output(SInt(8.W))
    val sOutUpper = Output(SInt(8.W))

  })

  io.uOut := io.uIn
  io.sOut := io.sIn
  io.uOutLower := io.uIn % 128.U
  io.uOutUpper := (io.uIn % 128.U) + 128.U
  io.sOutLower := io.sIn % 64.S
  io.sOutUpper := (io.sIn % 64.S) << 4
}

class InstrumentMeTester(c: InstrumentMe) extends PeekPokeTester(c) {
  for {
    i <- 0 until 256
  } {
    val j = i - 128
    poke(c.io.uIn, i)
    poke(c.io.sIn, BigInt(j))
    step(1)
  }
}

class InstrumentingSpec extends FreeSpec with Matchers {
  "should get even graphs" in {
    val manager = new TesterOptionsManager {
      testerOptions = testerOptions.copy(backendName = "firrtl", testerSeed = 7L)
      interpreterOptions = interpreterOptions.copy(
        setVerbose = false, writeVCD = true,
        monitorBitUsage = true,
        monitorReportFileName = "gcd-signals.csv",
        monitorHistogramBins = 16,
        prettyPrintReport = true
      )
    }
    iotesters.Driver.execute(() => new InstrumentMe, manager) { c =>
      new InstrumentMeTester(c)
    } should be (true)
  }

}
