// See LICENSE for license details.

package chisel3.iotesters

import chisel3.internal.InstanceId
import chisel3.stage.{ChiselCircuitAnnotation, ChiselStage}
import chisel3.{Element, MemBase, MultiIOModule, assert}
import firrtl.stage.CompilerAnnotation
import firrtl.{AnnotationSeq, annoSeqToSeq}
import treadle.stage.TreadleTesterPhase
import treadle.{TreadleTester, TreadleTesterAnnotation}

private[iotesters] class TreadleBackend(
  dut: MultiIOModule,
  treadleTester: TreadleTester
)
extends Backend(_seed = System.currentTimeMillis()) {

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

      case mem: MemBase[_] =>
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

      case mem: MemBase[_] =>
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
          s"""EXPECT AT $stepNumber $msg  $name got ${bigIntToStr(got, base)}""" +
            s""" expected ${bigIntToStr(expected, base)}""" +
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

    // If we are here we do not want the default (or overriden) compiler, we want "low"
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(compilerName = "low")

    // get the chisel generator
    val generatorAnnotation = chisel3.stage.ChiselGeneratorAnnotation(dutGen)

    // This provides an opportunity to translate from top level generic flags to backend specific annos
    var annotationSeq: AnnotationSeq = optionsManager.toAnnotationSeq

    if(optionsManager.testerOptions.generateVcdOutput == "on") {
      annotationSeq = annotationSeq :+ treadle.WriteVcdAnnotation
    }

    annotationSeq = annotationSeq.flatMap {
      case _: firrtl.stage.CompilerAnnotation => None
      case a => Some(a)
    }

    // This produces a chisel circuit annotation, a later pass will generate a firrtl circuit
    // Can't do both at once currently because generating the latter deletes the former
    annotationSeq = (new chisel3.stage.phases.Elaborate).transform(annotationSeq :+ generatorAnnotation)

    val circuit = annotationSeq.collect { case x: ChiselCircuitAnnotation => x }.head.circuit
    val dut = getTopModule(circuit).asInstanceOf[T]

    // This generates the firrtl circuit needed by the TreadleTesterPhase
    // Uses low compiler to avoid padWidths changing Dshl to Dshlw which blows up CheckTypes
    annotationSeq = (new ChiselStage).execute(Array("-X", "low"), annotationSeq)

    // This generates a TreadleTesterAnnotation with a treadle tester instance
    annotationSeq = (new TreadleTesterPhase).transform(annotationSeq)

    val treadleTester = annotationSeq.collectFirst { case TreadleTesterAnnotation(t) => t }.getOrElse(
      throw new Exception(
        s"TreadleTesterPhase could not build a treadle tester from these annotations" +
          annotationSeq.mkString("Annotations:\n", "\n  ", "")
      )
    )

    (dut, new TreadleBackend(dut, treadleTester))
  }
}
