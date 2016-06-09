// See LICENSE for license details.
package chisel.iotesters

import java.io.File

import chisel._

import scala.collection.mutable.HashMap

import firrtl_interpreter.InterpretiveTester

private[iotesters] class FirrtlTerpBackend(dut: Module, firrtlIR: String, verbose: Boolean = true) extends Backend {
  val interpretiveTester = new InterpretiveTester(firrtlIR)
  reset(5)//reset firrtl interpreter on construction

  private val ioNameMap = {
    val result = HashMap[Data, String]()
    def getFirrtlName(arg: (Bits, (String, String))) = arg match {case (io, (name, _)) =>
      result(io) = name
    }
    val (inputMap, outputMap) = getPortNameMaps(dut)
    (inputMap map getFirrtlName, outputMap map getFirrtlName)
    result
  }

  private def getIPCName(data: Data) = ioNameMap getOrElse (data, "<no signal name>")

  override def poke(signal: Bits, value: BigInt): Unit = {
    val name = getIPCName(signal)
    interpretiveTester.poke(name, value)
    if (verbose) println(s"  POKE ${name} <- ${bigIntToStr(value, 16)}")
  }

  override def peek(signal: Bits): BigInt = {
    val name = getIPCName(signal)
    val result = interpretiveTester.peek(name)
    if (verbose) println(s"  PEEK ${name} -> ${bigIntToStr(result, 16)}")
    result
  }

  override def expect(signal: Bits, expected: BigInt, msg: => String = "") : Boolean = {
    val name = getIPCName(signal)
    val got = interpretiveTester.peek(name)
    val good = got == expected
    if (verbose) println(s"""${msg}  EXPECT ${name} -> ${bigIntToStr(got, 16)} == ${bigIntToStr(expected, 16)} ${if (good) "PASS" else "FAIL"}""")
    good
  }

  override def step(n: Int): Unit = {
    interpretiveTester.step(n)
  }

  override def reset(n: Int = 1): Unit = {
    interpretiveTester.poke("reset", 1)
    interpretiveTester.step(n)
    interpretiveTester.poke("reset", 0)
  }

  override def finish: Unit = Unit
}

private[iotesters] object setupFirrtlTerpBackend {
  def apply(dutGen: ()=> chisel.Module): Backend = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val dir = new File(testDirPath)
    dir.mkdirs()

    val circuit = chisel.Driver.elaborate(dutGen)
    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${testDirPath}/${circuit.name}.ir"
    chisel.Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    val firrtlIR = chisel.Driver.emit(dutGen)

    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)
    new FirrtlTerpBackend(dut, firrtlIR)
  }
}
