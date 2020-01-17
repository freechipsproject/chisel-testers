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

import org.scalatest.{ Matchers, FlatSpec}

import chisel3._
import chisel3.iotesters._

class HasCycle extends Module {
  val io = IO( new Bundle {
    val a = Input(Bool())
    val o = Output(Bool())
  })

  val b = Wire(Bool())
  b := b&&io.a

  io.o := b
}

class HasCycleTester( c:HasCycle) extends PeekPokeTester(c) {
  poke( c.io.a, 0)
  step(1)
}

class HasCycleTest extends FlatSpec with Matchers {
  behavior of "HasCycle"

  it should "work in the interpreter" in {
    chisel3.iotesters.Driver.execute(
      // interpreter has it's own loop detector that needs to be disabled as well with --fr-allow-cycles
      Array( "--no-check-comb-loops", "--backend-name", "firrtl", "--fr-allow-cycles"),
      () => new HasCycle) { c =>
      new HasCycleTester( c)
    } should be ( true)
  }
  it should "work in verilator" in {
    chisel3.iotesters.Driver.execute(
              Array( "--no-check-comb-loops", "--backend-name", "verilator"),
      () => new HasCycle) { c =>
      new HasCycleTester( c)
    } should be ( true)
  }
}
