// See LICENSE for license details.

package chisel3.iotesters

import chisel3.{ChiselExecutionSuccess, Element, Mem, assert}
import chisel3.experimental.MultiIOModule
import chisel3.internal.InstanceId
import firrtl.{FirrtlExecutionFailure, FirrtlExecutionSuccess, LowForm}
import treadle.TreadleTester

private[iotesters] class TreadleBackend(
  dut: MultiIOModule,
  firrtlIR: String,
  optionsManager: TesterOptionsManager = new TesterOptionsManager
)
extends Backend(_seed = System.currentTimeMillis()) {

  val treadleTester = new TreadleTester(firrtlIR, optionsManager, LowForm)
  reset(5) // reset firrtl treadle on construction

  private val portNames = dut.getPorts.flatMap { case chisel3.internal.firrtl.Port(id, dir) =>
    val pathName = id.pathName
    getDataNames(pathName.drop(pathName.indexOf('.') + 1), id)
  }.toMap

  def poke(signal: InstanceId, value: BigInt, off: Option[Int])
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    signal match {
      case port: Element =>
        val name = portNames(port)
        treadleTester.poke(name, value)
        if (verbose) logger info s"  POKE $name <- ${bigIntToStr(value, base)}"

      case mem: Mem[_] =>
        val memoryName = mem.pathName.split("""\.""").tail.mkString(".")
        treadleTester.pokeMemory(memoryName, off.getOrElse(0), value)
        if (verbose) logger info s"  POKE MEMORY $memoryName <- ${bigIntToStr(value, base)}"

      case _ =>
    }
  }

  def poke(signal: InstanceId, value: Int, off: Option[Int])
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    poke(signal, BigInt(value), off)
  }

  def peek(signal: InstanceId, off: Option[Int])
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    signal match {
      case port: Element =>
        val name = portNames(port)
        val result = treadleTester.peek(name)
        if (verbose) logger info s"  PEEK $name -> ${bigIntToStr(result, base)}"
        result

      case mem: Mem[_] =>
        val memoryName = mem.pathName.split("""\.""").tail.mkString(".")

        treadleTester.peekMemory(memoryName, off.getOrElse(0))

      case _ => BigInt(rnd.nextInt)
    }
  }

  def expect(signal: InstanceId, expected: BigInt, msg: => String)
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    signal match {
      case port: Element =>
        val name = portNames(port)
        val got = treadleTester.peek(name)
        val good = got == expected
        if (verbose || !good) logger info
          s"""EXPECT AT $stepNumber $msg  $name got ${bigIntToStr(got, base)} expected ${bigIntToStr(expected, base)}""" +
            s""" ${if (good) "PASS" else "FAIL"}"""
        if(good) treadleTester.expectationsMet += 1
        good
      case _ => false
    }
  }

  def expect(signal: InstanceId, expected: Int, msg: => String)
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    expect(signal,BigInt(expected), msg)
  }

  def poke(path: String, value: BigInt)
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    assert(false)
  }

  def peek(path: String)
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    assert(false)
    BigInt(rnd.nextInt)
  }

  def expect(path: String, expected: BigInt, msg: => String)
    (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    assert(false)
    false
  }

  private var stepNumber: Long = 0L

  def step(n: Int)(implicit logger: TestErrorLog): Unit = {
    stepNumber += n
    treadleTester.step(n)
  }

  def reset(n: Int = 1): Unit = {
    treadleTester.poke("reset", 1)
    treadleTester.step(n)
    treadleTester.poke("reset", 0)
  }

  def finish(implicit logger: TestErrorLog): Unit = {
    treadleTester.report()
  }
}

private[iotesters] object setupTreadleBackend {
  def apply[T <: MultiIOModule](
    dutGen: () => T,
    optionsManager: TesterOptionsManager = new TesterOptionsManager): (T, Backend) = {

    // the backend must be treadle if we are here, therefore we want the firrtl compiler
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(compilerName = "low")
    // Workaround to propagate Annotations generated from command-line options to second Firrtl
    // invocation, run after updating compilerName so we only get one emitCircuit annotation
    val annos = Driver.filterAnnotations(firrtl.Driver.getAnnotations(optionsManager))
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(annotations = annos.toList)

    // generate VcdOutput overrides setting of writeVcd
    if(optionsManager.testerOptions.generateVcdOutput == "on") {
      optionsManager.treadleOptions = optionsManager.treadleOptions.copy(writeVCD = true)
    }

    chisel3.Driver.execute(optionsManager, dutGen) match {
      case ChiselExecutionSuccess(Some(circuit), _, Some(firrtlExecutionResult)) =>
        val dut = getTopModule(circuit).asInstanceOf[T]
        firrtlExecutionResult match {
          case success: FirrtlExecutionSuccess =>
            val compiledFirrtl = success.emitted
            optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(
              annotations = success.circuitState.annotations.toList
            )
            (dut, new TreadleBackend(dut, compiledFirrtl, optionsManager = optionsManager))
          case FirrtlExecutionFailure(message) =>
            throw new Exception(s"FirrtlBackend: failed firrlt compile message: $message")
        }
      case _ =>
        throw new Exception("Problem with compilation")
    }
  }
}

