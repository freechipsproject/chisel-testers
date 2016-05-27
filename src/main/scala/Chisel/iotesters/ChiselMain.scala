// See LICENSE for license details.

package Chisel.iotesters

import Chisel._

import scala.collection.mutable.{ArrayBuffer}
import scala.util.{DynamicVariable}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.io.{File, IOException}

private[iotesters] class TesterContext {
  var isVCS = false
  var isGenVerilog = false
  var isGenHarness = false
  var isCompiling = false
  var isRunTest = false
  var testerSeed = System.currentTimeMillis
  val testCmd = ArrayBuffer[String]()
  var targetDir = new File("test_run_dir").getCanonicalPath
}

object chiselMain {
  private val contextVar = new DynamicVariable[Option[TesterContext]](None)
  private[iotesters] def context = contextVar.value getOrElse (new TesterContext)

  private def parseArgs(args: Array[String]) {
    for (i <- 0 until args.size) {
      args(i) match {
        case "--vcs" => context.isVCS = true
        case "--v" => context.isGenVerilog = true
        case "--backend" => {
          if(args(i+1) == "v") {
            context.isGenVerilog = true
          } else if(args(i+1) == "c") {
            context.isGenVerilog = true
          }
        }
        case "--genHarness" => context.isGenHarness = true
        case "--compile" => {
          context.isCompiling = true
        }
        case "--test" => {
          context.isRunTest = true
        }
        case "--testCommand" => context.testCmd ++= args(i+1) split ' '
        case "--targetDir" => context.targetDir = args(i+1)
        case _ =>
      }
    }
  }

  private def genVerilog(dutGen: () => Module) {
    val circuit = Driver.elaborate(dutGen)
    val dir = new File(context.targetDir)
    dir.mkdirs()
    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${dir}/${circuit.name}.ir"
    Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    // Generate Verilog
    val verilogFilePath = s"${dir}/${circuit.name}.v"
    firrtl.Driver.compile(firrtlIRFilePath, verilogFilePath, new firrtl.VerilogCompiler())
  }

  private def genHarness[T <: Module](dutGen: () => Module, isVCS: Boolean, verilogFileName:String, cppHarnessFilePath:String, vcdFilePath: String) {
    if (isVCS) {
      assert(false, "unimplemented")
    } else {
      genVerilatorCppHarness(dutGen, verilogFileName, cppHarnessFilePath, vcdFilePath)
    }
  }

  private def compile(dutName: String) {
    // Copy API files
    copyVerilatorHeaderFiles(s"${context.targetDir}")

    val dir = new File(context.targetDir)
    dir.mkdirs()
    if (context.isVCS) {
    } else {
      // Generate Verilator
      val harness = new File(s"${dir}/${dutName}-harness.cpp")
      Driver.verilogToCpp(dutName, dutName, dir, Seq(), harness).!
      // Compile Verilator
      Driver.cppToExe(dutName, dir).!
    }
  }

  private def elaborate[T <: Module](args: Array[String], dutGen: () => T): T = {
    parseArgs(args)
    try {
      Files.createDirectory(Paths.get(context.targetDir))
    } catch {
      case x: FileAlreadyExistsException =>
      case x: IOException =>
        System.err.format("createFile error: %s%n", x)
    }
    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)

    if (context.isGenVerilog) genVerilog(dutGen)

    if (context.isGenHarness) genHarness(dutGen, context.isVCS, s"${dut.name}.v", s"${chiselMain.context.targetDir}/${dut.name}-harness.cpp", s"${chiselMain.context.targetDir}/${dut.name}.vcd")
    if (context.isCompiling) compile(dut.name)
    if (context.testCmd.isEmpty) {
      context.testCmd += s"""${context.targetDir}/${if (context.isVCS) "" else "V"}${dut.name}"""
    }
    dut
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T): T = {
    val ctx = Some(new TesterContext)
    val dut = contextVar.withValue(ctx) {
      elaborate(args, dutGen)
    }
    contextVar.value = ctx // TODO: is it ok?
    dut
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T, testerGen: T => PeekPokeTester[T]) = {
    contextVar.withValue(Some(new TesterContext)) {
      val dut = elaborate(args, dutGen)
      if(context.isRunTest) {
        assert(testerGen(dut).finish, "Test failed")
      }
      dut
    }
  }
}

object chiselMainTest {
  def apply[T <: Module](args: Array[String], dutGen: () => T)(testerGen: T => PeekPokeTester[T]) = {
    chiselMain(args, dutGen, testerGen)
  }
}
