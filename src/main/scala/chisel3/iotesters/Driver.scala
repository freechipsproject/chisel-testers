// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

import chisel3.{HasChiselExecutionOptions, Module}
import firrtl.{ComposableOptions, ExecutionOptionsManager, HasFirrtlOptions}
import firrtl_interpreter.HasInterpreterOptions

import scala.collection.mutable
import scala.util.DynamicVariable


case class TesterOptions(
   isGenVerilog:    Boolean = false,
   isGenHarness:    Boolean = false,
   isCompiling:     Boolean = false,
   isRunTest:       Boolean = false,
   testerSeed:      Long    = System.currentTimeMillis,
   testCmd:         mutable.ArrayBuffer[String]= mutable.ArrayBuffer[String](),
   backendName:     String  = "firrtl",
   logFileName:     String  = "",
   waveform:        Option[File] = None) extends ComposableOptions

trait HasTesterOptions {
  self: ExecutionOptionsManager =>

  var testerOptions = TesterOptions()

  parser.note("tester options")

  parser.opt[String]("backend-name")
    .abbr("tbn")
    .foreach { x => testerOptions = testerOptions.copy(backendName = x) }
    .text("run this as test command")

  parser.opt[Unit]("is-gen-verilog")
    .abbr("tigv")
    .foreach { _ => testerOptions = testerOptions.copy(isGenVerilog = true) }
    .text("has verilog already been generated")

  parser.opt[Unit]("is-gen-harness")
    .abbr("tigh")
    .foreach { _ => testerOptions = testerOptions.copy(isGenHarness = true) }
    .text("has harness already been generated")

  parser.opt[Unit]("is-compiling")
    .abbr("tic")
    .foreach { _ => testerOptions = testerOptions.copy(isCompiling = true) }
    .text("has harness already been generated")

  parser.opt[Seq[String]]("test-command")
    .abbr("ttc")
    .foreach { x => testerOptions = testerOptions.copy(testCmd = testerOptions.testCmd ++ x) }
    .text("run this as test command")

  parser.opt[String]("log-file-name")
    .abbr("tlfn")
    .foreach { x => testerOptions = testerOptions.copy(logFileName = x) }
    .text("write log file")

  parser.opt[File]("wave-form-file-name")
    .abbr("twffn")
    .foreach { x => testerOptions = testerOptions.copy(waveform = Some(x)) }
    .text("wave form file name")

  parser.opt[Long]("test-seed")
    .abbr("tts")
    .foreach { x => testerOptions = testerOptions.copy(testerSeed = x) }
    .text("provides a seed for random number generator")
}

object Driver {
  private val backendVar = new DynamicVariable[Option[Backend]](None)
  private[iotesters] def backend = backendVar.value

  def execute[T <: Module](
                            dutGenerator: () => T,
                            optionsManager: ExecutionOptionsManager
                              with HasTesterOptions
                              with HasChiselExecutionOptions
                              with HasFirrtlOptions
                              with HasInterpreterOptions
                          )
                          (
                            testerGen: T => PeekPokeTester[T]
                          ): Boolean = {
    val testerOptions = optionsManager.testerOptions

    val (dut, backend) = testerOptions.backendName match {
      case "firrtl"    =>
        setupFirrtlTerpBackend(dutGenerator, optionsManager)
      case "verilator" =>
        setupVerilatorBackend(dutGenerator)
      case "vcs"       =>
        setupVCSBackend(dutGenerator)
      case _ =>
        throw new Exception(s"Unrecognized backend name ${testerOptions.backendName}")
    }

    if(optionsManager.topName.isEmpty) {
      optionsManager.setTargetDirName(s"${optionsManager.targetDirName}/${testerGen.getClass.getName}")
    }

    backendVar.withValue(Some(backend)) {
      try {
        testerGen(dut).finish
      } catch { case e: Throwable =>
        e.printStackTrace()
        TesterProcess.killall
        throw e
      }
    }
  }

  def execute[T <: Module](args: Array[String], dut: () => T)(
    testerGen: T => PeekPokeTester[T]
  ): Boolean = {
    val optionsManager = new ExecutionOptionsManager("chisel-testers")
      with HasTesterOptions
      with HasChiselExecutionOptions
      with HasFirrtlOptions
      with HasInterpreterOptions

    optionsManager.parse(args) match {
      case true =>
        execute(dut, optionsManager)(testerGen)
        true
      case _ =>
        false
    }
  }
  /**
    * This is just here as command line way to see what the options are
    * It will not successfully run
    * TODO: Look into dynamic class loading as way to make this main useful
 *
    * @param args unused args
    */
  def main(args: Array[String]) {
    execute(Array("--help"), null)(null)
  }
  /**
    * Runs the ClassicTester and returns a Boolean indicating test success or failure
    * @@backendType determines whether the ClassicTester uses verilator or the firrtl interpreter to simulate the circuit
    * Will do intermediate compliation steps to setup the backend specified, including cpp compilation for the verilator backend and firrtl IR compilation for the firrlt backend
    */
  def apply[T <: Module](dutGen: () => T, backendType: String = "firrtl")(
      testerGen: T => PeekPokeTester[T]): Boolean = {
    val optionsManager = new ExecutionOptionsManager("chisel-testers")
      with HasTesterOptions
      with HasChiselExecutionOptions
      with HasFirrtlOptions
      with HasInterpreterOptions

    val (dut, backend) = backendType match {
      case "firrtl" => setupFirrtlTerpBackend(dutGen, optionsManager)
      case "verilator" => setupVerilatorBackend(dutGen)
      case "vcs" => setupVCSBackend(dutGen)
      case _ => throw new Exception("Unrecongnized backend type $backendType")
    }
    backendVar.withValue(Some(backend)) {
      try {
        testerGen(dut).finish
      } catch { case e: Throwable =>
        e.printStackTrace()
        TesterProcess.killall
        throw e
      }
    }
  }

  /**
    * Runs the ClassicTester using the verilator backend without doing Verilator compilation and returns a Boolean indicating success or failure
    * Requires the caller to supply path the already compile Verilator binary
    */
  def run[T <: Module](dutGen: () => T, cmd: Seq[String])
                      (testerGen: T => PeekPokeTester[T]): Boolean = {
    val circuit = chisel3.Driver.elaborate(dutGen)
    val dut = getTopModule(circuit).asInstanceOf[T]
    backendVar.withValue(Some(new VerilatorBackend(dut, cmd))) {
      try {
        testerGen(dut).finish
      } catch { case e: Throwable =>
        e.printStackTrace()
        TesterProcess.killall
        throw e
      }
    }
  }

  def run[T <: Module](dutGen: () => T, binary: String, args: String*)
                      (testerGen: T => PeekPokeTester[T]): Boolean =
    run(dutGen, binary +: args.toSeq)(testerGen)

  def run[T <: Module](dutGen: () => T, binary: File, waveform: Option[File] = None)
                      (testerGen: T => PeekPokeTester[T]): Boolean = {
    val args = waveform match {
      case None => Nil
      case Some(f) => Seq(s"+waveform=$f")
    }
    run(dutGen, binary.toString +: args.toSeq)(testerGen)
  }
}
