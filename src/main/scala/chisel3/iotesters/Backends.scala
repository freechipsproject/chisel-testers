// See LICENSE for license details.
package chisel3.iotesters

import chisel3.internal.InstanceId
import chisel3.experimental.MultiIOModule
import chisel3.internal.firrtl.Circuit

/**
  * define interface for ClassicTester backend implementations such as verilator and firrtl interpreter
  */

private[iotesters] abstract class Backend(private[iotesters] val _seed: Long = System.currentTimeMillis) {
  val rnd = new scala.util.Random(_seed)

  // Prepare the backend to (potentially generate and) run the simulation
  def prep[T <: MultiIOModule](
              dut: T,
              firrtlIR: Option[String] = None,
              circuitOption: Option[Circuit] = None,
              optionsManager: TesterOptionsManager = new TesterOptionsManager): Unit
  def dut: MultiIOModule
  // Generate the simulation harness (if required)
  def genHarness(): Unit = {}
  // Build the simulation
  def build(): Unit = {}
  // Run the simulation
  def run(cmd: Option[Seq[String]] = None): Unit

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


