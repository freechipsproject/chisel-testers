// See LICENSE for license details.
package Chisel.iotesters

import Chisel._

import scala.collection.mutable.HashMap
import scala.util.Random
import java.io.{File, PrintStream}

private[iotesters] object setupVerilatorBackend {
  def apply(dutGen: ()=> Chisel.Module): Backend = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val dir = new File(testDirPath)
    dir.mkdirs()

    val circuit = Chisel.Driver.elaborate(dutGen)
    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${testDirPath}/${circuit.name}.ir"
    Chisel.Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    // Generate Verilog
    val verilogFilePath = s"${testDirPath}/${circuit.name}.v"
    firrtl.Driver.compile(firrtlIRFilePath, verilogFilePath, new firrtl.VerilogCompiler)

    val verilogFileName = verilogFilePath.split("/").last
    val cppHarnessFileName = "classic_tester_top.cpp"
    val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
    val cppBinaryPath = s"${testDirPath}/V${circuit.name}"
    val vcdFilePath = s"${testDirPath}/${circuit.name}.vcd"

    copyVerilatorHeaderFiles(testDirPath)

    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)

    genVerilatorCppHarness(dutGen, verilogFileName, cppHarnessFilePath, vcdFilePath)
    Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), dut.name, new File(testDirPath), Seq(), new File(cppHarnessFileName)).!
    Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!

    new VerilatorBackend(dut, cppBinaryPath)
  }
}

private[iotesters] class VerilatorBackend(
                                          dut: Module, 
                                          cmd: String,
                                          verbose: Boolean = true,
                                          logger: PrintStream = System.out,
                                          _base: Int = 16,
                                          _seed: Long = System.currentTimeMillis) extends Backend {

  val simApiInterface = new SimApiInterface(dut, cmd, logger)
  val rnd = new Random(_seed)

  private val ioNameMap = {
    val result = HashMap[Data, String]()
    def getIPCName(arg: (Bits, (String, String))) = arg match {case (io, (_, name)) =>
      result(io) = name
    }
    val (inputMap, outputMap) = getPortNameMaps(dut)
    (inputMap map getIPCName, outputMap map getIPCName)
    result
  }

  private def getIPCName(data: Data) = ioNameMap getOrElse (data, "<no signal name>")

  override def poke(signal: Bits, value: BigInt) {
    val name = getIPCName(signal)
    if (verbose) logger println s"  POKE ${name} <- ${bigIntToStr(value, _base)}"
    simApiInterface.poke(name, value)
  }

  override def peek(signal: Bits) = {
    val name = getIPCName(signal)
    val result = simApiInterface.peek(name) getOrElse BigInt(rnd.nextInt)
    if (verbose) logger println s"  PEEK ${name} -> ${bigIntToStr(result, _base)}"
    result
  }

  override def expect(signal: Bits, expected: BigInt, msg: => String = "") = {
    val name = getIPCName(signal)
    val got = simApiInterface.peek(name) getOrElse BigInt(rnd.nextInt)
    val good = got == expected
    if (verbose) logger println s"""${msg}  EXPECT ${name} -> ${bigIntToStr(got, _base)} == ${bigIntToStr(expected, _base)} ${if (good) "PASS" else "FAIL"}"""
    good
  }

  override def step(n: Int): Unit = {
    simApiInterface.step(n)
  }

  override def reset(n: Int = 1): Unit = {
    simApiInterface.reset(n)
  }

  override def finish: Unit = {
    simApiInterface.finish
  }
}

