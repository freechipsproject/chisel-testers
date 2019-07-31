package examples

import chisel3._
import chisel3.experimental.MultiIOModule
import chisel3.iotesters.PeekPokeTester
import chisel3.util.Counter
import org.scalatest.{FreeSpec, Matchers}


class SecondClockDrivesRegisterSpec extends FreeSpec with Matchers {
  class SecondClock extends MultiIOModule {
    val inClock = IO(Input(Bool()))
    val out = IO(Output(UInt(8.W)))

    withClock(inClock.asClock) {
      out := Counter(true.B, 8)._1
    }
  }

  class SecondClockTester(c: SecondClock) extends PeekPokeTester(c) {
    poke(c.inClock, 0)
    expect(c.out, 0)

    // Main clock should do nothing
    step(1)
    expect(c.out, 0)
    step(1)
    expect(c.out, 0)

    // Output should advance on rising edge, even without main clock edge
    poke(c.inClock, 1)
    expect(c.out, 1)

    step(1)
    expect(c.out, 1)

    // Repeated, 1should do nothing
    poke(c.inClock, 1)
    expect(c.out, 1)

    // and again
    poke(c.inClock, 0)
    expect(c.out, 1)
    poke(c.inClock, 1)
    expect(c.out, 2)
  }

  "poking a clock should flip register" - {

    "should work with Treadle" in {
      iotesters.Driver.execute(Array(), () => new SecondClock) { c =>
        new SecondClockTester(c)
      } should be(true)
    }

    "should work with Verilator" in {
      iotesters.Driver.execute(Array("--backend-name", "verilator"), () => new SecondClock) { c =>
        new SecondClockTester(c)
      } should be(true)
    }
  }
}
