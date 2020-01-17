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

import chisel3._
import chisel3.util._
import chisel3.iotesters.experimental.{PokeTester, ImplicitPokeTester}

import org.scalatest._

class MyDut extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  io.out := io.in + 1.U
}

class PokeTesterSpec extends FlatSpec with PokeTester {
  "MyDut" should "properly add" in {
    test(new MyDut) {(t, c) =>
      t.poke(c.io.in, 0x41)
      t.step()
      t.expect(c.io.out, 0x42)

      t.poke(c.io.in, 0x0)
      t.step()
      t.expect(c.io.out, 0x1)
    }
  }
}

class ImplicitPokeTesterSpec extends FlatSpec with ImplicitPokeTester {
  "MyDut with implicits" should "properly add" in {
    test(new MyDut) {implicit t => c =>
      poke(c.io.in, 0x41)
      step()
      check(c.io.out, 0x42)

      poke(c.io.in, 0x0)
      step()
      check(c.io.out, BigInt(0x1))
    }
  }

  "Inline DUT with Bool/Booleans" should "work" in {
    test(new Module {
      val io = IO(new Bundle {
        val in = Input(Bool())
        val out = Output(Bool())
      })
      io.out := !io.in
    }) {implicit t => c =>
      poke(c.io.in, 0)
      step()
      check(c.io.out, 1)

      poke(c.io.in, true)
      step()
      check(c.io.out, false)
    }
  }
}
