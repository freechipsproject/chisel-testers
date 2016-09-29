// See LICENSE for license details.

package chisel3.iotesters

import chisel3.{ChiselExecutionOptions, Module}
import scopt.OptionParser
import scala.util.DynamicVariable
import java.io.File
import scala.collection.mutable

import firrtl.{CommonOptions, FirrtlExecutionOptions}

class TesterOptions(
                     var chiselOptions:   ChiselExecutionOptions,
                     var isGenVerilog:    Boolean = false,
                     var isGenHarness:    Boolean = false,
                     var isCompiling:     Boolean = false,
                     var isRunTest:       Boolean = false,
                     var testerSeed:      Long    = System.currentTimeMillis,
                     var testCmd:         mutable.ArrayBuffer[String]= mutable.ArrayBuffer[String](),
                     var backendName:     String  = "verilator",
                     var logFileName:     String  = "",
                     var waveform:        Option[File] = None
                   ) {
  trait ParserOptions {
    self: OptionParser[Unit] =>

    note("tester options")

    opt[Unit]("is-gen-verilog").abbr("tigv").foreach { _ => isGenVerilog = true }
      .text("has verilog already been generated")

    opt[Unit]("is-gen-harness").abbr("tigh").foreach { _ => isGenHarness = true }
      .text("has harness already been generated")

    opt[Unit]("is-compiling").abbr("tic").foreach { _ => isCompiling = true }
      .text("has harness already been generated")

    opt[String]("test-command").abbr("ttc").foreach { x => testCmd ++= x.split(' ') }
      .text("run this as test command")

    opt[String]("log-file-name").abbr("tlfn").foreach { x => logFileName = x }
      .text("write log file")

    opt[String]("wave-form-file-name").abbr("twffn").foreach { x =>
      new java.io.File(x)
    }.text("wave form file name")

    opt[Long]("test-seed").abbr("tts").foreach { x => testerSeed = x }
      .text("provides a seed for random number generator")
  }

  def commonOptions: CommonOptions = chiselOptions.firrtlExecutionOptions.commonOptions
  def firrtlOptions: FirrtlExecutionOptions = chiselOptions.firrtlExecutionOptions
}

object Driver {
  private val backendVar = new DynamicVariable[Option[Backend]](None)
  private[iotesters] def backend = backendVar.value

  def execute[T <: Module](testerOptions: TesterOptions, dut: () => T): Boolean = {
    testerOptions.backendName match {
      case "firrtl"    =>
      case "verilator" =>
      case "vcs"       =>
      case _ =>
        throw new Exception(s"Unrecognized backend name ${testerOptions.backendName}")
    }
    false
  }

  def execute[T <: Module](args: Array[String], dut: () => T): Boolean = {
    val commonOptions = new CommonOptions
    val firrtlExecutionOptions = new FirrtlExecutionOptions(commonOptions = commonOptions)
    val chiselExecutionOptions = new ChiselExecutionOptions(firrtlExecutionOptions = firrtlExecutionOptions)
    val testerOptions          = new TesterOptions(chiselOptions = chiselExecutionOptions)
    val parser = new OptionParser[Unit]("chisel-tester")
      with commonOptions.ParserOptions
      with testerOptions.ParserOptions
      with chiselExecutionOptions.ParserOptions
      with firrtlExecutionOptions.ParserOptions

    parser.parse(args) match {
      case true =>
        true
      case _ =>
        false
    }
  }
  /**
    * Runs the ClassicTester and returns a Boolean indicating test success or failure
    * @@backendType determines whether the ClassicTester uses verilator or the firrtl interpreter to simulate the circuit
    * Will do intermediate compliation steps to setup the backend specified, including cpp compilation for the verilator backend and firrtl IR compilation for the firrlt backend
    */
  def apply[T <: Module](dutGen: () => T, backendType: String = "firrtl")(
      testerGen: T => PeekPokeTester[T]): Boolean = {
    val (dut, backend) = backendType match {
      case "firrtl" => setupFirrtlTerpBackend(dutGen)
      case "verilator" => setupVerilatorBackend(dutGen)
      case "vcs" => setupVCSBackend(dutGen)
      case _ => throw new Exception("Unrecongnized backend type $backendType")
    }
    backendVar.withValue(Some(backend)) {
      try {
        testerGen(dut).finish
      } catch { case e: Throwable =>
        e.printStackTrace
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
        e.printStackTrace
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
