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

package chisel3.iotesters

import chisel3.internal.InstanceId
import java.io.PrintStream

/**
  * define interface for ClassicTester backend implementations such as verilator and firrtl interpreter
  */

private[iotesters] abstract class Backend(private[iotesters] val _seed: Long = System.currentTimeMillis) {
  val rnd = new scala.util.Random(_seed)

  def poke(signal: InstanceId, value: BigInt, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit

  def peek(signal: InstanceId, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt

  def poke(path: String, value: BigInt)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit

  def peek(path: String)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt

  def expect(signal: InstanceId, expected: BigInt)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean =
    expect(signal, expected, "")

  def expect(signal: InstanceId, expected: BigInt, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean

  def expect(path: String, expected: BigInt)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean =
    expect(path, expected, "")

  def expect(path: String, expected: BigInt, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean

  def step(n: Int)(implicit logger: TestErrorLog): Unit

  def reset(n: Int): Unit

  def finish(implicit logger: TestErrorLog): Unit
}


