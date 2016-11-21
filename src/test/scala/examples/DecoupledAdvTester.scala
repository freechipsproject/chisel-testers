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
    // The following is used to decouple ready/valid but introduces a delay of one cycle.
    val valid = Reg(Bool())
    // We'll need to delay the result appropriately.
    val result = Reg(UInt(width = filter_width))

    when (io.in.valid && isOk(io.in.bits)) {
      result := io.in.bits
      valid := Bool(true)
    } otherwise {
      valid := Bool(false)
    }

    io.out.bits := result
    io.out.valid := io.out.ready && valid
  }

  val io = IO(new FilterIO())

  val smalls = Module(new Filter(_ < UInt(10)))
  // Because of the way the AdvTester works:
  //  peeks for ready execute in the "current" cycle, while pokes occur in the next cycle
  //  we need to ensure that ready won't be de-asserted during the poke cycle (we only get one chance to assert "valid").
  //  A queue depth of 2 should be sufficient to ensure this.
  val q      = Module(new Queue(UInt(width = filter_width), entries = 2))
  val odds   = Module(new Filter((x: UInt) => (x & UInt(1)) === UInt(1)))

  smalls.io.in <> io.in
  q.io.enq     <> smalls.io.out
  odds.io.in   <> q.io.deq
  io.out       <> odds.io.out
}

class SmallOdds5Tester(dut: SmallOdds5) extends AdvTester(dut) {
  val max_tick_count = 8
  val testerInputDriver = DecoupledSource(dut.io.in)
  val testerOutputHandler = DecoupledSink(dut.io.out)
  for (i <- 0 to 300) {
    val num = rnd.nextInt(20)
    testerInputDriver.inputs.enqueue(num)
    if (num % 2 == 1 && num < 10) {
      // Since we may not have stepped the circuit while we were enqueuing test samples,
      //  ensure we run sufficient cycles to drain the queue.
      eventually(testerOutputHandler.outputs.size != 0, max_tick_count + testerInputDriver.inputs.size)
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


