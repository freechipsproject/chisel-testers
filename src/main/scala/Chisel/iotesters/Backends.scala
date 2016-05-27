// See LICENSE for license details.
package Chisel.iotesters

import Chisel._

/**
  * define interface for ClassicTester backend implementations such as verilator and firrtl interpreter
  */

abstract class Backend {
  def poke(data: Bits, signal: BigInt): Unit

  def peek(data: Bits): BigInt

  def expect(signal: Bits, expected: BigInt, msg: => String = "") : Boolean

  def step(n: Int): Unit

  def reset(n: Int = 1): Unit

  def finish: Unit
}


