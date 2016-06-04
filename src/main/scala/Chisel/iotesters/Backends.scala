// See LICENSE for license details.
package Chisel.iotesters

import Chisel.internal.HasId

/**
  * define interface for ClassicTester backend implementations such as verilator and firrtl interpreter
  */

abstract class Backend(_seed: Long = System.currentTimeMillis) {
  val rnd = new scala.util.Random(_seed)

  def poke(data: HasId, value: BigInt, off: Option[Int] = None): Unit

  def peek(data: HasId, off: Option[Int] = None): BigInt

  def expect(signal: HasId, expected: BigInt, msg: => String = "") : Boolean

  def step(n: Int): Unit

  def reset(n: Int = 1): Unit

  def finish: Unit
}


