/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package examples

import chisel3._
import chisel3.util.{EnqIO, DeqIO, Queue}
import chisel3.iotesters.{ChiselFlatSpec, OrderedDecoupledHWIOTester}

class SmallOdds3(filter_width: Int) extends Module {

  class FilterIO extends Bundle {
    val in = DeqIO(UInt(filter_width.W))
    val out = EnqIO(UInt(filter_width.W))
  }

  class Filter(isOk: UInt => Bool) extends Module {
    val io = IO(new FilterIO)

    io.in.ready  := io.out.ready
    io.out.bits  := io.in.bits

    io.out.valid := io.out.ready && io.in.valid && isOk(io.in.bits)
  }

  val io = IO(new FilterIO())

  def buildFilter(): Unit = {
    val smalls = Module(new Filter(_ < 10.U))
    val q      = Module(new Queue(UInt(filter_width.W), entries = 1))
    val odds   = Module(new Filter((x: UInt) => (x & 1.U) === 1.U))

    io.in.ready         := smalls.io.in.ready

    smalls.io.in.valid  := io.in.valid
    smalls.io.in.bits   := io.in.bits
    smalls.io.out.ready := q.io.enq.ready

    q.io.enq.valid      := smalls.io.out.valid
    q.io.enq.bits       := smalls.io.out.bits
    q.io.deq.ready      := odds.io.in.ready

    odds.io.in.valid    := q.io.deq.valid
    odds.io.in.bits     := q.io.deq.bits
    odds.io.out.ready   := io.out.ready

    io.out.bits         := odds.io.out.bits
    io.out.valid        := odds.io.out.valid
  }

  buildFilter()
}

class SmallOdds3Tester(width: Int) extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new SmallOdds3(filter_width = width))

  rnd.setSeed(0L)
  for (i <- 0 to 100) {
    val num = rnd.nextInt(20)
    println(s"random value $i $num")
    inputEvent(device_under_test.io.in.bits -> num)
    if (num % 2 == 1 && num < 10) {
      outputEvent(device_under_test.io.out.bits -> num)
    }
  }
}

class SmallOdds3TesterSpec extends ChiselFlatSpec {
  "a small odds filters" should "take a stream of UInt and only pass along the odd ones < 10" in {
    assertTesterPasses {
      new SmallOdds3Tester(32)
    }
  }
}


