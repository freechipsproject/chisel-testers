// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

import chisel3._
import chisel3.core.{Aggregate, Element}
import PeekPokeTester.extractElementBits
import chisel3.experimental.{FixedPoint, MultiIOModule}
import chisel3.internal.firrtl.KnownBinaryPoint

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// Provides a template to define tester transactions
trait PeekPokeTests {
  def t: Long
  def rnd: scala.util.Random
  implicit def int(x: Boolean): BigInt
  implicit def int(x: Int):     BigInt
  implicit def int(x: Long):    BigInt
  implicit def int(x: Bits):    BigInt
  def println(msg: String = ""): Unit
  def reset(n: Int): Unit
  def step(n: Int): Unit
  def poke(path: String, x: BigInt): Unit
  def peek(path: String): BigInt
  def poke(signal: Bits, x: BigInt): Unit
  def pokeAt[T <: Bits](signal: Mem[T], x: BigInt, off: Int): Unit
  def peek(signal: Bits): BigInt
  def peekAt[T <: Bits](signal: Mem[T], off: Int): BigInt
  def expect(good: Boolean, msg: => String): Boolean
  def expect(signal: Bits, expected: BigInt, msg: => String = ""): Boolean
  def finish: Boolean
}

object PeekPokeTester {
  /** Old "flatten" functionality.
    *
    * @param signal - Chisel type for which individual elements are required.
    * @return [[IndexedSeq[Element]]]
    */
  private def extractElementBits(signal: Data): IndexedSeq[Element] = {
    signal match {
      case elt: Aggregate => elt.getElements.toIndexedSeq flatMap {extractElementBits(_)}
      case elt: Element => IndexedSeq(elt)
      case elt => throw new Exception(s"Cannot extractElementBits for type ${elt.getClass}")
    }
  }
}

abstract class PeekPokeTester[+T <: MultiIOModule](
    val dut: T,
    base: Int = 16,
    logFile: Option[File] = None) {

  implicit val logger = new TestErrorLog

  implicit def longToInt(x: Long) = x.toInt
  val optionsManager = Driver.optionsManager

  implicit val _verbose = optionsManager.testerOptions.isVerbose
  implicit val _base    = optionsManager.testerOptions.displayBase

  def println(msg: String = "") {
    logger.info(msg)
  }

  /****************************/
  /*** Simulation Interface ***/
  /****************************/
  val backend = Driver.backend.get

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

  val rnd = backend.rnd
  rnd.setSeed(optionsManager.testerOptions.testerSeed)
  println(s"SEED ${optionsManager.testerOptions.testerSeed}")

  /** Convert a Boolean to BigInt */
  implicit def int(x: Boolean): BigInt = if (x) 1 else 0
  /** Convert Bits to BigInt */
  implicit def int(x: Bits):    BigInt = x.litValue()

  /**
    * Convert an Int to unsigned (effectively 32-bit) BigInt
    * @param x  number to be converted
    * @return
    */
  def intToUnsignedBigInt(x: Int): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

  /**
    * Convert an Int to unsigned (effectively 64-bit) BigInt
    * @param x long to be converted
    * @return
    */
  def longToUnsignedBigInt(x: Long): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

  def reset(n: Int = 1) {
    backend.reset(n)
  }

  def step(n: Int) {
    if (_verbose) println(s"STEP $simTime -> ${simTime+n}")
    backend.step(n)
    incTime(n)
  }

  def poke(path: String, value: BigInt): Unit = {
    backend.poke(path, value)
  }
  def poke(path: String, value: Int): Unit = {
    poke(path, BigInt(value))
  }

  def poke(path: String, value: Long): Unit = {
    poke(path, BigInt(value))
  }

  def peek(path: String) = backend.peek(path)

  def poke(signal: Bits, value: BigInt): Unit = {
    if (!signal.isLit) backend.poke(signal, value, None)
    // TODO: Warn if signal.isLit
  }

  def poke(signal: Bits, value: Int) {
    poke(signal, BigInt(value))
  }

  def poke(signal: Bits, value: Long) {
    poke(signal, BigInt(value))
  }

  def pokeFixedPoint(signal: FixedPoint, value: Double): Unit = {
    val bigInt = value.F(signal.width, signal.binaryPoint).litValue()
    poke(signal, bigInt)
  }

  /** Locate a specific bundle element, given a name path.
    * TODO: Handle Vecs
    *
    * @param path - list of element names (presumably bundles) terminating in a non-bundle (i.e., Bits) element.
    * @param bundle - bundle containing the element
    * @return the element (as Bits)
    */
  private def getBundleElement(path: List[String], bundle: ListMap[String, Data]): Bits = {
    (path, bundle(path.head)) match {
      case (head :: Nil, element: Bits) => element
      case (head :: tail, b: Bundle) => getBundleElement(tail, b.elements)
      case _ => throw new Exception(s"peek/poke bundle element mismatch $path")
    }
  }

  /** Poke a Bundle given a map of elements and values.
    *
    * @param signal the bundle to be poked
    * @param map a map from names (using '.' to delimit bundle elements), to BigInt values
    */
  def poke(signal: Bundle, map: Map[String, BigInt]): Unit =  {
    val circuitElements = signal.elements
    for ( (key, value) <- map) {
      val subKeys = key.split('.').toList
      val element = getBundleElement(subKeys, circuitElements)
      poke(element, value)
    }
  }

  def poke(signal: Aggregate, value: IndexedSeq[BigInt]): Unit =  {
    (extractElementBits(signal) zip value.reverse).foreach(x => poke(x._1.asInstanceOf[Bits], x._2))
  }

  def pokeAt[TT <: Bits](data: Mem[TT], value: BigInt, off: Int): Unit = {
    backend.poke(data, value, Some(off))
  }

  def peek(signal: Bits):BigInt = {
    if (!signal.isLit) backend.peek(signal, None) else signal.litValue()
  }

  def peekFixedPoint(signal: FixedPoint): Double = {
    val bigInt = peek(signal)
    signal.binaryPoint match {
      case KnownBinaryPoint(bp) => FixedPoint.toDouble(bigInt, bp)
      case _ => throw new Exception("Cannot peekFixedPoint with unknown binary point location")
    }
  }

  def peek(signal: Aggregate): Seq[BigInt] =  {
    extractElementBits(signal) map (x => backend.peek(x.asInstanceOf[Bits], None))
  }

  /** Populate a map of names ("dotted Bundles) to Bits.
    * TODO: Deal with Vecs
    *
    * @param map the map to be constructed
    * @param indexPrefix an array of Bundle name prefixes
    * @param signalName the signal to be added to the map
    * @param signalData the signal object to be added to the map
    */
  private def setBundleElement(map: mutable.LinkedHashMap[String, Bits], indexPrefix: ArrayBuffer[String], signalName: String, signalData: Data): Unit = {
    indexPrefix += signalName
    signalData match {
      case bundle: Bundle =>
        for ((name, value) <- bundle.elements) {
          setBundleElement(map, indexPrefix, name, value)
        }
      case bits: Bits =>
        val index = indexPrefix.mkString(".")
        map(index) = bits
    }
    indexPrefix.remove(indexPrefix.size - 1)
  }

  /** Peek an aggregate (Bundle) signal.
    *
    * @param signal the signal to peek
    * @return a map of signal names ("dotted" Bundle) to BigInt values.
    */
  def peek(signal: Bundle): mutable.LinkedHashMap[String, BigInt] = {
    val bitsMap = mutable.LinkedHashMap[String, Bits]()
    val index = ArrayBuffer[String]()
    // Populate the Bits map.
    for ((elementName, elementValue) <- signal.elements) {
      setBundleElement(bitsMap, index, elementName, elementValue)
    }
    val bigIntMap = mutable.LinkedHashMap[String, BigInt]()
    for ((name, bits) <- bitsMap) {
      bigIntMap(name) = peek(bits)
    }
    bigIntMap
  }

  def peekAt[TT <: Bits](data: Mem[TT], off: Int): BigInt = {
    backend.peek(data, Some(off))
  }

  def expect (good: Boolean, msg: => String): Boolean = {
    if (_verbose || ! good) println(s"""EXPECT AT $simTime $msg ${if (good) "PASS" else "FAIL"}""")
    if (!good) fail
    good
  }

  def expect(signal: Bits, expected: BigInt, msg: => String = ""): Boolean = {
    if (!signal.isLit) {
      val good = backend.expect(signal, expected, msg)
      if (!good) fail
      good
    } else expect(signal.litValue() == expected, s"${signal.litValue()} == $expected")
  }

  def expect(signal: Bits, expected: Int, msg: => String): Boolean = {
    expect(signal, BigInt(expected), msg)
  }

  def expectFixedPoint(signal: FixedPoint, expected: Double, msg: => String, epsilon: Double = 0.01): Boolean = {
    val double = peekFixedPoint(signal)

    expect((double - expected).abs < epsilon, msg )
  }

  def expect (signal: Aggregate, expected: IndexedSeq[BigInt]): Boolean = {
    (extractElementBits(signal), expected.reverse).zipped.foldLeft(true) { (result, x) => result && expect(x._1.asInstanceOf[Bits], x._2)}
  }

  /** Return true or false if an aggregate signal (Bundle) matches the expected map of values.
    * TODO: deal with Vecs
    *
    * @param signal the Bundle to "expect"
    * @param expected a map of signal names ("dotted" Bundle notation) to BigInt values
    * @return true if the specified values match, false otherwise.
    */
  def expect (signal: Bundle, expected: Map[String, BigInt]): Boolean = {
    val bitsMap = mutable.LinkedHashMap[String, Bits]()
    val index = ArrayBuffer[String]()
    for ((elementName, elementValue) <- signal.elements) {
      setBundleElement(bitsMap, index, elementName, elementValue)
    }
    expected.forall{ case ((name, value)) => expect(bitsMap(name), value) }
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
    println(s"""RAN $simTime CYCLES ${if (ok) "PASSED" else s"FAILED FIRST AT CYCLE $failureTime"}""")
    logger.report()
    ok
  }
}
