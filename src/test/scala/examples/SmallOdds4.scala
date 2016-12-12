// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util.{EnqIO, DeqIO, Queue}
import chisel3.iotesters.{ChiselFlatSpec, OrderedDecoupledHWIOTester}

class SmallOdds4(filter_width: Int) extends Module {

  class FilterIO extends Bundle {
    val in = DeqIO(UInt(filter_width.W))
    val out = EnqIO(UInt(filter_width.W))
  }

  class Filter(isOk: UInt => Bool) extends Module {
    val io = IO(new FilterIO)

    io.in.ready := io.out.ready
    io.out.bits := io.in.bits

    io.out.valid := io.out.ready && io.in.valid && isOk(io.in.bits)
  }

  val io = IO(new FilterIO())

  def buildFilter(): Unit = {
    val smalls = Module(new Filter(_ < 10.U))
    val q      = Module(new Queue(UInt(filter_width.W), entries = 1))
    val odds   = Module(new Filter((x: UInt) => (x & 1.U) === 1.U))

    io.in.ready  := smalls.io.in.ready
    smalls.io.in <> io.in
    q.io.enq     <> smalls.io.out
    odds.io.in   <> q.io.deq
    io.out       <> odds.io.out
  }

  buildFilter()
}

class SmallOdds4Tester(width: Int) extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new SmallOdds4(filter_width = width))

  OrderedDecoupledHWIOTester.max_tick_count = 4000

  for (i <- 0 to 30) {
    val num = rnd.nextInt(20)
    logScalaDebug(s"random value $i $num")
    inputEvent(device_under_test.io.in.bits -> num)
    if (num % 2 == 1 && num < 10) {
      outputEvent(device_under_test.io.out.bits -> num)
    }
  }
}

class SmallOdds4TesterSpec extends ChiselFlatSpec {
  val testWidth = 32
  "a small odds filters" should "take a stream of UInt and only pass along the odd ones < 10" in {
    assertTesterPasses {
      new SmallOdds4Tester(testWidth)
    }
  }
}


