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

import org.scalatest.{FreeSpec, Matchers}
import chisel3._
import chisel3.iotesters.PeekPokeTester
import logger.LazyLogging

class HasSyncReadMem extends MultiIOModule {
  val readAddr  = IO(Input(UInt(16.W)))
  val readData  = IO(Output(UInt(16.W)))
  val writeAddr = IO(Input(UInt(16.W)))
  val writeData = IO(Input(UInt(16.W)))
  val writeEnable = IO(Input(Bool()))

  val mem = SyncReadMem(10, UInt(16.W))

  readData := mem(readAddr)
  when(writeEnable) {
    mem(writeAddr) := writeData
  }
}

class SyncReadMemTest extends FreeSpec with Matchers {
  "peekAt and pokeAt should work with treadle" in {
    iotesters.Driver.execute(
      Array(
        "--backend-name",
        "treadle",
        "--target-dir",
        "test_run_dir/sync_read_mem_test_treadle",
        "--top-name",
        "SyncReadMem"
      ),
      () => new HasSyncReadMem
    ) { c =>
      new PeekPokeTester(c) {
        poke(c.writeEnable, 1)
        for (i <- 0 until 8) {
          poke(c.writeAddr, i)
          poke(c.writeData, i + 30)
          step(1)
        }
        poke(c.writeEnable, 0)
        for (i <- 0 until 8) {
          poke(c.readAddr, i)
          step(1)
          val memValue = peek(c.readData)
          memValue should be(i + 30)
          logger.info(s"$i -> $memValue")
        }
        for (i <- 0 until 8) {
          pokeAt(c.mem, i + 20, i)
        }
        step(1)
        for (i <- 0 until 8) {
          val memValue = peekAt(c.mem, i)
          logger.info(s"$i -> $memValue")
          memValue should be(i + 20)

        }
      }
    } should be(true)
  }
}
