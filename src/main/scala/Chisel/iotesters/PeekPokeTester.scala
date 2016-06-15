// See LICENSE for license details.

package Chisel.iotesters

import Chisel._

import scala.util.Random

// Provides a template to define tester transactions
private [iotesters] trait PeekPokeTests {
  type DUT <: Module
  def dut: DUT
  def t: Long
  def rnd: Random
  implicit def int(x: Boolean): BigInt
  implicit def int(x: Int):     BigInt
  implicit def int(x: Long):    BigInt
  implicit def int(x: Bits):    BigInt
  def reset(n: Int): Unit
  def step(n: Int): Unit
  def poke(data: Bits, x: BigInt): Unit
  def peek(data: Bits): BigInt
  def expect(good: Boolean, msg: => String): Boolean
  def expect(data: Bits, expected: BigInt, msg: => String = ""): Boolean
  def finish: Boolean
}


abstract class PeekPokeTester[+T <: Module](
                                            val dut: T,
                                            verbose: Boolean = true,
                                            _backend: Option[Backend] = None,
                                            _seed: Long = System.currentTimeMillis) {

  implicit def longToInt(x: Long) = x.toInt

  /****************************/
  /*** Simulation Interface ***/
  /****************************/
  println(s"SEED ${_seed}")
  val backend = _backend getOrElse(new VerilatorBackend(dut, verbose=verbose, _seed=_seed))

  /********************************/
  /*** Classic Tester Interface ***/
  /********************************/
  /* Simulation Time */
  private var simTime = 0L
  protected[iotesters] def incTime(n: Int) { simTime += n }
  def t = simTime

  /** Indicate a failure has occurred.  */
  private var failureTime = -1L
  private var ok = true
  def fail = if (ok) {
    failureTime = simTime
    ok = false
  }

  val rnd = new Random(_seed)

  /** Convert a Boolean to BigInt */
  implicit def int(x: Boolean): BigInt = if (x) 1 else 0
  /** Convert an Int to BigInt */
  implicit def int(x: Int):     BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  /** Convert a Long to BigInt */
  implicit def int(x: Long):    BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  /** Convert Bits to BigInt */
  implicit def int(x: Bits):    BigInt = x.litValue()

  def reset(n: Int = 1) {
    backend.reset(n)
  }

  def step(n: Int) {
    if (verbose) println(s"STEP ${simTime} -> ${simTime+n}")
    backend.step(n)
    incTime(n)
  }

  def poke(signal: Bits, value: BigInt): Unit = {
    backend.poke(signal, value)
  }

  def poke(signal: Aggregate, value: IndexedSeq[BigInt]): Unit =  {
    (signal.flatten zip value.reverse).foreach(x => poke(x._1, x._2))
  }

  def pokeAt[T <: Bits](data: Mem[T], value: BigInt, off: Int): Unit = {
    assert(false, "not yet implemented")
  }

  def peek(signal: Bits):BigInt = {
    val result = backend.peek(signal)
    result
  }

  def peek(signal: Aggregate): IndexedSeq[BigInt] =  {
    signal.flatten map (x => backend.peek(x))
  }

  def peekAt[T <: Bits](data: Mem[T], off: Int): BigInt = {
    assert(false, "not yet implemented")
    BigInt(0)
  }

  def expect (good: Boolean, msg: => String): Boolean = {
    if (verbose) println(s"""EXPECT ${msg} ${if (good) "PASS" else "FAIL"}""")
    if (!good) fail
    good
  }

  def expect(signal: Bits, expected: BigInt, msg: => String = ""): Boolean = {
    val good = backend.expect(signal, expected, msg)
    if (!good) fail
    good
  }

  def expect (signal: Aggregate, expected: IndexedSeq[BigInt]): Boolean = {
    (signal.flatten, expected.reverse).zipped.foldLeft(true) { (result, x) => result && expect(x._1, x._2)}
  }

  def finish: Boolean = {
    try {
      backend.finish
    } catch {
      // Depending on load and timing, we may get a TestApplicationException
      //  when the test application exits.
      //  Check the exit value.
      //  Anything other than 0 is an error.
      case e: TestApplicationException => if (e.exitVal != 0) fail
    }
    println(s"""RAN ${simTime} CYCLES ${if (ok) "PASSED" else s"FAILED FIRST AT CYCLE ${failureTime}"}""")
    ok
  }
}
