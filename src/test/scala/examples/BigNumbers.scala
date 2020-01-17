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
import chisel3.iotesters.{PeekPokeTester, TesterOptionsManager}
import org.scalatest.{FreeSpec, Matchers}

class PassOn extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(UInt(64.W))
  })
  io.out := io.in
}

class BigNumbersTester(c: PassOn) extends PeekPokeTester(c) {
  poke (c.io.in, 0x0000000070000000L)
  expect(c.io.out, 0x0000000070000000L)

  // Test 2:(Test Fails)
  poke (c.io.in, 0x0000000770000000L)
  expect (c.io.out, 0x0000000770000000L)

  // Output only takes value of last 32 bits (70000000) and test fails.

  // Test 3:(FIRRTL generates an error)
  poke (c.io.in, 0x0000000080000000L)
  expect (c.io.out, 0x0000000080000000L)
}

class BigNumbersSpec extends FreeSpec with Matchers {
  "big numbers should work with interpreter backend" in {
    iotesters.Driver.execute(() => new PassOn, new TesterOptionsManager) { c =>
      new BigNumbersTester(c)
    } should be(true)

  }
}
