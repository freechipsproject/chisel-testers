// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util.{EnqIO, DeqIO, Queue}
import chisel3.iotesters.{ChiselFlatSpec, AdvTester}

class SmallOdds5(filter_width: Int) extends Module {

  class FilterIO extends Bundle {
    val in = DeqIO(UInt(width = filter_width))
    val out = EnqIO(UInt(width = filter_width))
  }

  class Filter(isOk: UInt => Bool) extends Module {
    val io = IO(new FilterIO)

    io.in.ready := io.out.ready
    io.out.bits := io.in.bits
    val valid = Reg(Bool())

    valid := io.in.valid && isOk(io.in.bits)

    io.out.valid := io.out.ready && valid
  }

  val io = IO(new FilterIO())

  val smalls = Module(new Filter(_ < UInt(10)))
  val q      = Module(new Queue(UInt(width = filter_width), entries = 1))
  val odds   = Module(new Filter((x: UInt) => (x & UInt(1)) === UInt(1)))

  io.in.ready  := smalls.io.in.ready
  smalls.io.in <> io.in
  q.io.enq     <> smalls.io.out
  odds.io.in   <> q.io.deq
  io.out       <> odds.io.out

}

class SmallOdds5Tester(dut: SmallOdds5) extends AdvTester(dut) {
  val max_tick_count = 6

  for (i <- 0 to 30) {
    val num = rnd.nextInt(20)
    val testerInputDriver = DecoupledSource(dut.io.in)
    val testerOutputHandler = DecoupledSink(dut.io.out)
    testerInputDriver.inputs.enqueue(num)
    if (num % 2 == 1 && num < 10) {
      eventually(testerOutputHandler.outputs.size != 0, max_tick_count)
      val result = testerOutputHandler.outputs.dequeue()
      expect(result == num, s"tester output ($result) == $num")
    }
  }
}

class DecoupledAdvTesterSpec extends ChiselFlatSpec {
  val testWidth = 32
  "a small odds filter using the AdvTester" should "take a stream of UInt and only pass along the odd ones < 10" in {
    assert {
      chisel3.iotesters.Driver(() => new SmallOdds5(testWidth)) { c =>
        new SmallOdds5Tester(c)
      }
    }
  }
}


