// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

/* Written by Stephen Twigg, Eric Love */
import chisel3._
import chisel3.util._

import scala.collection.mutable.ArrayBuffer
import java.io.{PrintWriter, StringWriter}
// Provides a template to define advanced tester transactions
//
@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
trait AdvTests extends PeekPokeTests {
  def cycles: Long
  def wire_poke[T <: Element: Pokeable](port: T, target: BigInt):  Unit
  def reg_poke[T <: Element: Pokeable](port: T, target: BigInt):   Unit
  def takestep(work: => Unit = {}): Unit
  def takesteps(n: Int)(work: =>Unit = {}): Unit
  def until(pred: =>Boolean, maxCycles: Long = 0L)(work: =>Unit): Boolean
  def eventually(pred: =>Boolean, maxCycles: Long = 0L): Boolean
  def do_until(work: =>Unit)(pred: =>Boolean, maxCycles: Long = 0L): Boolean
}

@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
abstract class AdvTester[+T <: Module](dut: T,
                                       base: Int = 16,
                                       logFile: Option[java.io.File] = chiselMain.context.logFile)
                extends PeekPokeTester(dut, base, logFile) {
  val defaultMaxCycles = 1024L
  var _cycles = 0L
  def cycles = _cycles
  override def incTime(n: Int) {
    _cycles += n
    super.incTime(n)
  }

  // List of scala objects that need to be processed along with the test benches, like sinks and sources
  val preprocessors = new ArrayBuffer[Processable]()
  val postprocessors = new ArrayBuffer[Processable]()
  // pre v post refers to when user-customized update code ('work') is processed
  // e.g. sinks are in the preprocessing list and sources in the postprocessing list
  //    this allows the testbench to respond to a request within one cycle

  // This section of code lets testers easily emulate have registers right before dut inputs
  //   This testing style conforms with the general ASPIRE testbench style
  // Also, to ensure difference enforced, poke 'deprecated' and replaced with wire_poke
  def wire_poke[T <: Element: Pokeable](port: T, target: BigInt) = super.poke(port, target)

  override def poke[T <: Element: Pokeable](port: T, target: BigInt) {
    require(false, "poke hidden for AdvTester, use wire_poke or reg_poke")
  }

  private val registered_bits_updates = new scala.collection.mutable.HashMap[Element,BigInt]()
  private def do_registered_updates() = {
    registered_bits_updates.foreach{case (key, value) => key match {
      case Pokeable(p) => wire_poke(p, value)
    }}
    registered_bits_updates.clear
  }

  def reg_poke[T <: Element: Pokeable](port: T, target: BigInt) { registered_bits_updates(port) = target }

  // This function replaces step in the advanced tester and makes sure all tester features are clocked in the appropriate order
  def takestep(work: => Unit = {}): Unit = {
    try {
      step(1)
      do_registered_updates()
      preprocessors.foreach(_.process()) // e.g. sinks
      work
      postprocessors.foreach(_.process())
    } catch {
      case e: Throwable =>
        fail
        val sw = new StringWriter
        val pw = new PrintWriter(sw)
        e.printStackTrace(pw)
        logger.info(pw.toString)
        assert(finish, "test fail")
    }
  }
  def takesteps(n: Int)(work: =>Unit = {}): Unit = {
    require(n > 0, "Number of steps taken must be positive integer.")
    (0 until n).foreach(_ => takestep(work))
  }

  // Functions to step depending on predicates
  def until(pred: =>Boolean, maxCycles: Long = defaultMaxCycles)(work: =>Unit): Boolean = {
    var timeout_cycles = 0L
    while(!pred && (timeout_cycles < maxCycles)) {
      takestep(work)
      timeout_cycles += 1
    }
    assert(timeout_cycles < maxCycles,
      "until timed out after %d cycles".format(timeout_cycles))
    pred
  }
  def eventually(pred: =>Boolean, maxCycles: Long = defaultMaxCycles) = {until(pred, maxCycles){}}
  def do_until(work: =>Unit)(pred: =>Boolean, maxCycles: Long = defaultMaxCycles): Boolean = {
    takestep(work)
    until(pred, maxCycles){work}
  }

  def assert(expr: Boolean, errMsg:String = "") = {
    if (!expr && !errMsg.isEmpty) fail
    expr
  }

  class IrrevocableSink[T <: Data, R]( socket: ReadyValidIO[T], cvt: T=>R, 
    max_count: Option[Int] = None ) extends Processable
  {
    socket match {
      case s: IrrevocableIO[T] =>
      case s: DecoupledIO[T] => {
        logger.warning("Potentially unsafe conversion of DecoupledIO output to IrrevocableIO")
      }
      case _ => {
        logger.warning("Potentially unsafe conversion of ReadyValidIO output to IrrevocableIO")
      }
    }

    val outputs = new scala.collection.mutable.Queue[R]()
    private var amReady = true
    private def isValid = peek(socket.valid) == 1

    def process() = {
      // Handle this cycle
      if(isValid && amReady) {
        outputs.enqueue(cvt(socket.bits))
      }
      // Decide what to do next cycle and post onto register
      amReady = max_count match { case None => true case Some(p) => outputs.length <= p }
      reg_poke(socket.ready, amReady)
    }

    // Initialize
    wire_poke(socket.ready, 1)
    preprocessors += this
  }

  object IrrevocableSink {
    def apply[T<:Element: Pokeable](socket: ReadyValidIO[T]) =
      new IrrevocableSink(socket, (socket_bits: T) => peek(socket_bits))
  }

  class DecoupledSink[T <: Data, R]( socket: ReadyValidIO[T], cvt: T=>R,
    max_count: Option[Int] = None ) extends IrrevocableSink(socket,cvt,max_count)
  {
    logger.warning("DecoupledSink is deprecated. Use IrrevocableSink")
  }

  object DecoupledSink {
    def apply[T<:Element: Pokeable](socket: ReadyValidIO[T]) =
      new DecoupledSink(socket, (socket_bits: T) => peek(socket_bits))
  }

  class ValidSink[T <: Data, R]( socket: ValidIO[T], cvt: T=>R ) extends Processable
  {
    val outputs = new scala.collection.mutable.Queue[R]()
    private def isValid = peek(socket.valid) == 1

    def process() = {
      if(isValid) {
        outputs.enqueue(cvt(socket.bits))
      }
    }

    // Initialize
    preprocessors += this
  }
  object ValidSink {
    def apply[T<:Element: Pokeable](socket: ValidIO[T]) =
      new ValidSink(socket, (socket_bits: T) => peek(socket_bits))
  }

  class DecoupledSource[T <: Data, R]( socket: DecoupledIO[T], post: (T,R)=>Unit ) extends Processable
  {
    val inputs = new scala.collection.mutable.Queue[R]()

    private var amValid = false
    private var justFired = false
    private def isReady = (peek(socket.ready) == 1)
    def isIdle = !amValid && inputs.isEmpty && !justFired

    def process() = {
      justFired = false
      if(isReady && amValid) { // Fired last cycle
        amValid = false
        justFired = true
      }
      if(!amValid && !inputs.isEmpty) {
        amValid = true
        post(socket.bits, inputs.dequeue())
      }
      reg_poke(socket.valid, amValid)
    }

    // Initialize
    wire_poke(socket.valid, 0)
    postprocessors += this
  }
  object DecoupledSource {
    def apply[T<:Element: Pokeable](socket: DecoupledIO[T]) =
      new DecoupledSource(socket, (socket_bits: T, in: BigInt) => reg_poke(socket_bits, in))
  }

  class ValidSource[T <: Data, R]( socket: ValidIO[T], post: (T,R)=>Unit ) extends Processable
  {
    val inputs = new scala.collection.mutable.Queue[R]()
    private var amValid = false
    private var justFired = false

    def isIdle = inputs.isEmpty && !amValid

    def process() = {
      // Always advance the input
      justFired = (amValid==true)
      amValid = false
      if(!inputs.isEmpty) {
        amValid = true
        post(socket.bits, inputs.dequeue())
      }
      reg_poke(socket.valid, amValid)
    }

    // Initialize
    wire_poke(socket.valid, 0)
    postprocessors += this
  }
  object ValidSource {
    def apply[T<:Element: Pokeable](socket: ValidIO[T]) =
      new ValidSource(socket, (socket_bits: T, in: BigInt) => reg_poke(socket_bits, in))
  }
}

trait Processable {
  def process(): Unit
}

