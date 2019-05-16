// See LICENSE for license details.

package chisel3.iotesters

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.util.DynamicVariable

import chisel3._

private[iotesters] class TesterContext {
  var isGenVerilog = false
  var isGenHarness = false
  var isCompiling = false
  var isRunTest = false
  var testerSeed: Long = System.currentTimeMillis
  val testCmd: ArrayBuffer[String] = ArrayBuffer[String]()
  var backendType = "verilator"
  var targetDir = "test_run_dir"
  var logFileName: Option[String] = None
  var waveformName: Option[String] = None
}

object chiselMain {
  private val contextVar = new DynamicVariable[Option[TesterContext]](None)
  private def context = contextVar.value.getOrElse(new TesterContext)

  private def parseArgs(args: List[String]): Unit = args match {
    case "--firrtl" :: tail => context.backendType = "firrtl" ; parseArgs(tail)
    case "--verilator" :: tail => context.backendType = "verilator" ; parseArgs(tail)
    case "--vcs" :: tail => context.backendType = "vcs" ; parseArgs(tail)
    case "--glsim" :: tail => context.backendType = "glsim" ; parseArgs(tail)
    case "--v" :: tail  => context.isGenVerilog = true ; parseArgs(tail)
    case "--backend" :: value :: tail => context.backendType = value ; parseArgs(tail)
    case "--genHarness" :: tail => context.isGenHarness = true ; parseArgs(tail)
    case "--compile" :: tail => context.isCompiling = true ; parseArgs(tail)
    case "--test" :: tail => context.isRunTest = true ; parseArgs(tail)
    case "--testCommand" :: value :: tail => context.testCmd ++= value split ' ' ; parseArgs(tail)
    case "--testerSeed" :: value :: tail => context.testerSeed = value.toLong ; parseArgs(tail)
    case "--targetDir" :: value :: tail => context.targetDir = value ; parseArgs(tail)
    case "--logFile" :: value :: tail => context.logFileName = Some(value) ; parseArgs(tail)
    case "--waveform" :: value :: tail => context.waveformName = Some(value) ; parseArgs(tail)
    case _ :: tail => parseArgs(tail) // skip unknown flag
    case Nil => // finish
  }

  private def convertOldArgsToOptions(args: Array[String]): TesterOptionsManager = {
    parseArgs(args.toList)
    val optionsManager = new TesterOptionsManager
    if (context.isGenVerilog)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(isGenVerilog = true)
    if (context.isGenHarness)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(isGenHarness = true)
    if (context.isCompiling)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(isCompiling = true)
    if (!context.isRunTest)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(isRunTest = false)

    optionsManager.testerOptions = optionsManager.testerOptions.copy(testerSeed = context.testerSeed)
    optionsManager.testerOptions = optionsManager.testerOptions.copy(testCmd = context.testCmd)
    context.backendType match {
      case "firrtl"|"ivl"|"treadle"|"vcs"|"verilator" =>
        optionsManager.testerOptions = optionsManager.testerOptions.copy(backendName = context.backendType)
      case "glsim" =>
        optionsManager.testerOptions = optionsManager.testerOptions.copy(backendName = "vcs")
      case b => throw BackendException(b)
    }

    optionsManager.commonOptions = optionsManager.commonOptions.copy(targetDirName = context.targetDir)
    if (context.logFileName.isDefined)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(logFileName = context.logFileName.get)
    if (context.waveformName.isDefined)
      optionsManager.testerOptions = optionsManager.testerOptions.copy(waveform = Some(new File(context.waveformName.get)))
    optionsManager
  }

  private def elaborate[T <: Module](om: TesterOptionsManager, dutGen: () => T): Option[T] = {
    if (om.makeTargetDir()) {
      val (dut, backend) = setupBackend(om, dutGen)
      Some(dut)
    } else {
      System.err.format("Couldn't create output directory: %s%n", om.targetDirName)
      None
    }
  }

  private def setupBackend[T <: Module](om: TesterOptionsManager, dutGenerator: () => T): (T, Backend) = {
    om.testerOptions.backendName match {
      case "firrtl" =>
        setupFirrtlTerpBackend(dutGenerator, om)
      case "treadle" =>
        setupTreadleBackend(dutGenerator, om)
      case "verilator" =>
        setupVerilatorBackend(dutGenerator, om)
      case "ivl" =>
        setupIVLBackend(dutGenerator, om)
      case "vcs" =>
        setupVCSBackend(dutGenerator, om)
      case _ =>
        throw new Exception(s"Unrecognized backend name ${om.testerOptions.backendName}")
    }
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T): T = {
    val ctx = Some(new TesterContext)
    val dut = contextVar.withValue(ctx) {
      val optionsManager = convertOldArgsToOptions(args)
      elaborate(optionsManager, dutGen).get
    }
    dut
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T, testerGen: T => PeekPokeTester[T]): Unit = {
    contextVar.withValue(Some(new TesterContext)) {
      val optionsManager = convertOldArgsToOptions(args)
      // Are we just elaborating or compiling?
      if (!optionsManager.testerOptions.isRunTest) {
        elaborate(optionsManager, dutGen)
      } else {
        Driver.execute(dutGen, optionsManager)(testerGen)
      }
    }
  }
}

object chiselMainTest {
  def apply[T <: Module](args: Array[String], dutGen: () => T)(testerGen: T => PeekPokeTester[T]): Unit = {
    chiselMain(args, dutGen, testerGen)
  }
}
