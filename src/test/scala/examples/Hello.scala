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
import chisel3.iotesters.{SteppedHWIOTester, ChiselFlatSpec}
import chisel3.testers.TesterDriver

class Hello extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  io.out := 42.U
}

class HelloTester extends SteppedHWIOTester {
  val device_under_test = Module( new Hello )

  step(1)
  expect(device_under_test.io.out, 42)
}

object Hello {
  def main(args: Array[String]): Unit = {
    TesterDriver.execute { () => new HelloTester }
  }
}

class HelloSpec extends ChiselFlatSpec {
  "a" should "b" in {
    assertTesterPasses {  new HelloTester }
  }
}
