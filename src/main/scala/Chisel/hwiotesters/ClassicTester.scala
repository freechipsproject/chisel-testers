package Chisel.hwiotesters

import java.io.{File, IOException, PrintWriter}
import java.nio.channels.FileChannel
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{FileAlreadyExistsException, Files, Paths}

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, _}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Random
import Chisel._
import firrtl.{Parser, VerilogCompiler}

object chiselMainTest {
  def apply[T <: Module](args: Array[String], dutGenFunc: () => T)(testerGenFunc: T => ClassicTester): Unit = {
    println("DEBUG0")
    val rootDir = new File(".").getCanonicalPath()
    val dutModule = Chisel.Driver.elaborateModule(dutGenFunc)
    val dutName = dutModule.name
    val verilogFilePath = s"${rootDir}/${dutName}.v"
    //run Chisel 3
    val dutFirrtlIR = Chisel.Driver.emit(dutGenFunc)
    // Parse circuit into FIRRTL
    val circuit = firrtl.Parser.parse(dutFirrtlIR.split("\n"))
    val writer = new PrintWriter(new File(verilogFilePath))
    // Compile to verilog
    firrtl.VerilogCompiler.run(circuit, writer)
    //writer.write(dutFirrtlIR)
    writer.close()
    runClassicTester(dutGenFunc, verilogFilePath) {testerGenFunc}
  }
}

object runClassicTester {
  private def setupTestDir(testDirPath: String, verilogFilePath: String): Unit = {
    val verilogFileName = verilogFilePath.split("/").last
    val emulatorHFilePath = Paths.get(testDirPath + "/emulator.h")
    val simApiHFilePath = Paths.get(testDirPath + "/sim_api.h")
    val verilatorApiHFilePath = Paths.get(testDirPath + "/verilator_api.h")
    val newVerilogFilePath = Paths.get(testDirPath + "/" + verilogFileName)
    try {
      Files.createDirectory(Paths.get(testDirPath))
      Files.createFile(emulatorHFilePath)
      Files.createFile(simApiHFilePath)
      Files.createFile(verilatorApiHFilePath)
      Files.createFile(newVerilogFilePath)
    } catch {
      case x: FileAlreadyExistsException =>
        System.out.format("")
      case x: IOException => {
        System.err.format("createFile error: %s%n", x)
      }
    }

    Files.copy(Paths.get("src/main/resources/emulator.h"), emulatorHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get("src/main/resources/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get("src/main/resources/verilator_api.h"), verilatorApiHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get(verilogFilePath), newVerilogFilePath, REPLACE_EXISTING)
  }

  def apply[T <: Module] (dutGen: () => T, verilogFilePath: String) (testerGen: T => ClassicTester): Unit = {
    val dut = Chisel.Driver.elaborateModule(dutGen)
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val verilogFileName = verilogFilePath.split("/").last
    val cppHarnessFileName = "classic_tester_top.cpp"
    val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
    assert(genCppEmulatorBinaryPath(dut) == testDirPath + "/V" + verilogFilePath.split("/").last.split("\\.")(0))//check that the cpp binary path generated here is the same as the binary path expected by the ClassicTester
    setupTestDir(testDirPath, verilogFilePath)
    genClassicTesterCppHarness(dut, verilogFileName, cppHarnessFilePath)
    Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), new File(testDirPath), Seq(), new File(cppHarnessFilePath)).!
    Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!

    val tester = testerGen(dut)
    tester.finish()
  }
}

class ClassicTester(dut: Module) {
  private val (inputNodeInfoMap, outputNodeInfoMap) = getNodeInfo(dut)
  private val inputSignalToChunkSizeMap = new LinkedHashMap[String, Int]()
  inputNodeInfoMap.toList.foreach(x => inputSignalToChunkSizeMap(x._2._1) = (x._2._2 - 1)/64 + 1)
  println(inputSignalToChunkSizeMap)
  private val outputSignalToChunkSizeMap = new LinkedHashMap[String, Int]()
  outputNodeInfoMap.toList.foreach(x => outputSignalToChunkSizeMap(x._2._1) = (x._2._2 - 1)/64 + 1)
  private val nodeToStringMap: Map[Data, String] = (inputNodeInfoMap.toList ++ outputNodeInfoMap.toList).map(x => (x._1, x._2._1)).toMap
  private val cppEmulatorInterface = new CppEmulatorInterface(genCppEmulatorBinaryPath(dut), inputSignalToChunkSizeMap, outputSignalToChunkSizeMap, 0)
  val rnd = cppEmulatorInterface.rnd
  cppEmulatorInterface.start()//start cpp emulator

  def t = cppEmulatorInterface.getSimTime()

  def poke(signal: Bits, value: BigInt) = {
    cppEmulatorInterface.poke(nodeToStringMap(signal), value)
  }
  def poke(signal: Bundle, value: Array[BigInt]) = {
    assert(false)
    //cppEmulatorInterface.poke(nodeToStringMap(signal), value)
  }

  def peek(signal: Bits): BigInt = {
    cppEmulatorInterface.peek(nodeToStringMap(signal))
  }
  def peek(signal: Bundle): Array[BigInt] = {
    assert(false)
    Array(-1)
    //cppEmulatorInterface.peek(nodeToStringMap(signal))
  }

  def expect (signal: Bits, expected: BigInt, msg: => String): Boolean = {
    cppEmulatorInterface.expect(nodeToStringMap(signal), expected, msg)
  }
  def expect (signal: Bits, expected: BigInt): Boolean = {
    cppEmulatorInterface.expect(nodeToStringMap(signal), expected, "")
  }
  def expect (signal: Bool, msg: => String): Boolean = {
    cppEmulatorInterface.expect(nodeToStringMap(signal), 1, msg)
  }
  def expect (signal: Bool): Boolean = {
    cppEmulatorInterface.expect(nodeToStringMap(signal), 1, "")
  }

  def fail = cppEmulatorInterface.fail

  def step(n: Int) {
    cppEmulatorInterface.step(n)
  }
  def reset(n: Int = 1) = {
    cppEmulatorInterface.reset(n)
  }
  def finish(): Boolean = {
    cppEmulatorInterface.finish()
  }
}

object genClassicTesterCppHarness {
  def apply(dut: Module, verilogFileName: String, cppHarnessFilePath: String): Unit = {
    val (dutInputNodeInfo, dutOutputNodeInfo) = getNodeInfo(dut)
    val dutName = verilogFileName.split("\\.")(0)
    val dutApiClassName = dutName + "_api_t"
    val dutVerilatorClassName = "V" + dutName
    val fileWriter = new PrintWriter(new File(cppHarnessFilePath))

    fileWriter.write("#include \"" + dutVerilatorClassName + ".h\"\n")
    fileWriter.write("#include \"verilated.h\"\n")
    fileWriter.write("#include \"verilator_api.h\"\n")
    fileWriter.write("#include <iostream>\n")

    fileWriter.write(s"class ${dutApiClassName}: public sim_api_t<VerilatorDataWrapper*> {\n")
    fileWriter.write("public:\n")
    fileWriter.write(s"    ${dutApiClassName}(${dutVerilatorClassName}* _dut) {\n")
    fileWriter.write("        dut = _dut;\n")
    fileWriter.write("        is_exit = false;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    void init_sim_data() {\n")
    fileWriter.write("        sim_data.inputs.clear();\n")
    fileWriter.write("        sim_data.outputs.clear();\n")
    fileWriter.write("        sim_data.signals.clear();\n")
    dutInputNodeInfo.toList.foreach { x =>
      val nodeName = x._2._1
      val nodeWidth = x._2._2
      if (nodeWidth <= 8) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 16) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 32) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 64) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (nodeWidth - 1)/32 + 1
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    dutOutputNodeInfo.toList.foreach { x =>
      val nodeName = x._2._1
      val nodeWidth = x._2._2
      if (nodeWidth <= 8) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 16) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 32) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 64) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (nodeWidth - 1)/32 + 1
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    fileWriter.write("    }\n")
    fileWriter.write("    inline bool exit() { return is_exit; }\n")
    fileWriter.write("protected:\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* dut;\n")
    fileWriter.write("private:\n")
    fileWriter.write("    bool is_exit;\n")
    fileWriter.write("    virtual inline size_t put_value(VerilatorDataWrapper* &sig, uint64_t* data, bool force=false) {\n")
    fileWriter.write("        return sig->put_value(data);\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline size_t get_value(VerilatorDataWrapper* &sig, uint64_t* data) {\n")
    fileWriter.write("        return sig->get_value(data);\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline size_t get_chunk(VerilatorDataWrapper* &sig) {\n")
    fileWriter.write("        return sig->get_num_words();\n")
    fileWriter.write("    } \n")
    fileWriter.write("    virtual inline void reset() {\n")
    fileWriter.write("        dut->reset = 1;\n")
    fileWriter.write("        dut->clk = 1;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->reset = 0;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void start() { }\n")
    fileWriter.write("    virtual inline void finish() {\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        is_exit = true;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void step() {\n")
    fileWriter.write("        dut->clk = 0;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->clk = 1;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->clk = 0;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void update() {\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("    }\n")
    fileWriter.write("};\n")
    fileWriter.write("int main(int argc, char **argv, char **env) {\n")
    fileWriter.write("    Verilated::commandArgs(argc, argv);\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* top = new ${dutVerilatorClassName};\n")
    fileWriter.write(s"    ${dutApiClassName} api(top);\n")
    fileWriter.write("    api.init_sim_data();\n")
    fileWriter.write("    api.init_channels();\n")
    fileWriter.write("    while(!api.exit()) api.tick();\n")
    fileWriter.write("    delete top;\n")
    fileWriter.write("    exit(0);\n")
    fileWriter.write("}\n")
    fileWriter.close()
    println(s"ClassicTester CppHarness generated at ${cppHarnessFilePath}")
  }
}
object genCppEmulatorBinaryPath {
  def apply(dut: Module): String = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    testDirPath + "/V" + dut.name
  }
}

object getNodeInfo {
  def apply(dut: Module): (LinkedHashMap[Data, (String, Int)], LinkedHashMap[Data, (String, Int)]) = {
    val inputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()
    val outputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()

    def add_to_ports_by_direction(port: Data, name: String, width: Int): Unit = {
      port.dir match {
        case INPUT => inputNodeInfoMap(port) = (name, width)
        case OUTPUT => outputNodeInfoMap(port) = (name, width)
        case _ =>
      }
    }

    def parseBundle(b: Bundle, name: String = ""): Unit = {
      for ((n, e) <- b.namedElts) {
        val new_name = name + (if (name.length > 0) "_" else "") + n

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element => add_to_ports_by_direction(e, new_name, e.getWidth)
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }
    def parseVecs[T <: Data](b: Vec[T], name: String = ""): Unit = {
      for ((e, i) <- b.zipWithIndex) {
        val new_name = name + s"_$i"
        add_to_ports_by_direction(e, new_name, e.getWidth)

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element =>
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }

    parseBundle(dut.io, "io")
    (inputNodeInfoMap, outputNodeInfoMap)
  }
}
class CppEmulatorInterface(val cmd: String, val inputSignalToChunkSizeMap: LinkedHashMap[String, Int], val outputSignalToChunkSizeMap: LinkedHashMap[String, Int], testerSeed: Int) {
  private object SIM_CMD extends Enumeration {
    val RESET, STEP, UPDATE, POKE, PEEK, FORCE, GETID, GETCHK, SETCLK, FIN = Value }
  implicit def cmdToId(cmd: SIM_CMD.Value) = cmd.id

  /* state variables */
  private val inputSignalValueMap = LinkedHashMap[String, BigInt]()
  private val outputSignalValueMap = LinkedHashMap[String, BigInt]()
  private val _logs = new ArrayBuffer[String]()
  private var simTime = 0L // simulation time
  private var isStale = false
  val rnd = new Random(testerSeed)
  /* state variables */

  /* standalone util functions */
  implicit def longToInt(x: Long) = x.toInt
  /** Convert a Boolean to BigInt */
  private def int(x: Boolean): BigInt = if (x) 1 else 0
  /** Convert an Int to BigInt */
  private def int(x: Int):     BigInt = (BigInt(x >>> 1) << 1) | x & 1
  /** Convert a Long to BigInt */
  private def int(x: Long):    BigInt = (BigInt(x >>> 1) << 1) | x & 1
  protected def bigIntToStr(x: BigInt, base: Int) = base match {
    case 2  if x < 0 => s"-0b${(-x).toString(base)}"
    case 16 if x < 0 => s"-0x${(-x).toString(base)}"
    case 2  => s"0b${x.toString(base)}"
    case 16 => s"0x${x.toString(base)}"
    case _ => x.toString(base)
  }
  /* standalone util functions */

  /* state modification functions */
  def getSimTime() = simTime
  private def incTime(n: Int) { simTime += n }
  private def printfs = _logs.toVector
  private def throwExceptionIfDead(exitValue: Future[Int]) {
    if (exitValue.isCompleted) {
      val exitCode = Await.result(exitValue, Duration(-1, SECONDS))
      // We assume the error string is the last log entry.
      val errorString = if (_logs.size > 0) {
        _logs.last
      } else {
        "test application exit"
      } + " - exit code %d".format(exitCode)
      throw new TestApplicationException(exitCode, errorString)
    }
  }
  // A busy-wait loop that monitors exitValue so we don't loop forever if the test application exits for some reason.
  private def mwhile(block: => Boolean)(loop: => Unit) {
    while (!exitValue.isCompleted && block) {
      loop
    }
    // If the test application died, throw a run-time error.
    throwExceptionIfDead(exitValue)
  }

  private def sendCmd(data: Int) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }
  private def sendCmd(data: String) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }
  private def recvResp = {
    outChannel.aquire
    val valid = outChannel.valid
    val resp = if (!valid) None else {
      outChannel.consume
      Some(outChannel(0).toInt)
    }
    outChannel.release
    resp
  }
  private def sendValue(value: BigInt, chunk: Int) = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (0 until chunk) foreach (i => inChannel(i) = (value >> (64*i)).toLong)
      inChannel.produce
    }
    inChannel.release
    ready
  }
  private def recvValue(chunk: Int) = {
    outChannel.aquire
    val valid = outChannel.valid
    val value = if (!valid) None else {
      outChannel.consume
      Some(((0 until chunk) foldLeft BigInt(0))(
        (res, i) => res | (int(outChannel(i)) << (64*i))))
    }
    outChannel.release
    value
  }

  private def recvOutputs = {
    outputSignalValueMap.clear
    outChannel.aquire
    val valid = outChannel.valid
    if (valid) {
      (outputSignalToChunkSizeMap.keys.toList foldLeft 0){case (off, out) =>
        val chunk = outputSignalToChunkSizeMap(out)
        outputSignalValueMap(out) = ((0 until chunk) foldLeft BigInt(0))(
          (res, i) => res | (int(outChannel(off + i)) << (64 * i))
        )
        off + chunk
      }
      outChannel.consume
    }
    outChannel.release
    valid
  }
  private def sendInputs = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (inputSignalToChunkSizeMap.keys.toList foldLeft 0){case (off, in) =>
        val chunk = inputSignalToChunkSizeMap(in)
        val value = inputSignalValueMap getOrElse (in, BigInt(0))
        (0 until chunk) foreach (i => inChannel(off + i) = (value >> (64 * i)).toLong)
        off + chunk
      }
      inChannel.produce
    }
    inChannel.release
    ready
  }

  def reset(n: Int = 1) {
    for (i <- 0 until n) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }
  private def update {
    mwhile(!sendCmd(SIM_CMD.UPDATE)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }
  private def takeStep {
    mwhile(!sendCmd(SIM_CMD.STEP)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }
  private def getId(path: String) = {
    mwhile(!sendCmd(SIM_CMD.GETID)) { }
    mwhile(!sendCmd(path)) { }
    if (exitValue.isCompleted) {
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }
  private def getChunk(id: Int) = {
    mwhile(!sendCmd(SIM_CMD.GETCHK)) { }
    mwhile(!sendCmd(id)) { }
    if (exitValue.isCompleted){
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }
  def start() {
    println(s"SEED ${testerSeed}")
    println(s"STARTING ${cmd}")
    mwhile(!recvOutputs) { }
    reset(5)
    for (i <- 0 until 5) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }
  def poke(signalName: String, value: BigInt): Unit = {
    assert(inputSignalValueMap.contains(signalName))
    inputSignalValueMap(signalName) = value
    isStale = true
    println(s"  POKE ${signalName} <- ${bigIntToStr(value, 16)}")
  }
  def peek(signalName: String, mutePrintOut: Boolean = false): BigInt = {
    assert(inputSignalValueMap.contains(signalName) || outputSignalValueMap.contains(signalName))
    if (isStale) {
      update
    }
    var result: BigInt = -1
    if (inputSignalValueMap.contains(signalName)) {
      result = inputSignalValueMap(signalName)
    } else if(outputSignalValueMap.contains(signalName)) {
      result = outputSignalValueMap(signalName)
    }
    if (!mutePrintOut) {
      println(s"  PEEK ${signalName} -> ${bigIntToStr(result, 16)}")
    }
    result
  }
  def expect (signalName: String, expected: BigInt, msg: => String = ""): Boolean = {
    val got = peek(signalName, true)
    val good = got == expected
    if (!good) fail
    println(s"${msg}  EXPECT ${signalName} -> ${bigIntToStr(got, 16)} == ${bigIntToStr(expected, 16)}")
    println(s"${msg} ${if (good) "PASS" else "FAIL"}")
    good
  }
  def step(n: Int) {
    println(s"STEP ${simTime} -> ${simTime+n}")
    (0 until n) foreach (_ => takeStep)
    incTime(n)
  }
  /** Complete the simulation and inspect all tests */
  def finish(): Boolean = {
    try {
      mwhile(!sendCmd(SIM_CMD.FIN)) { }
      while(!exitValue.isCompleted) { }
    }
    catch {
      // Depending on load and timing, we may get a TestApplicationException
      //  when the test application exits.
      //  Check the exit value.
      //  Anything other than 0 is an error.
      case e: TestApplicationException => if (e.exitVal != 0) fail
    }
    _logs.clear
    inChannel.close
    outChannel.close
    cmdChannel.close
    println(s"""RAN ${simTime} CYCLES ${if (ok) "PASSED" else s"FAILED FIRST AT CYCLE ${failureTime}"}""")
    ok
  }
  /** Indicate a failure has occurred.  */
  private var failureTime = -1L
  private var ok = true
  def fail = if (ok) {
    failureTime = simTime
    ok = false
  }
  /* state modification functions */

  /*constructor logic*/
  //initialize inputSignalValueMap and outputSignalValueMap
  inputSignalToChunkSizeMap.foreach { case (signalName, chunkSize) => inputSignalValueMap(signalName) = 0 }
  outputSignalToChunkSizeMap.foreach { case (signalName, chunkSize) => outputSignalValueMap(signalName) = BigInt(rnd.nextInt) }

  //initialize cpp process and memory mapped channels
  private val (process: Process, exitValue: Future[Int], inChannel, outChannel, cmdChannel) = {
    val processBuilder = Process(cmd)
    val processLogger = ProcessLogger(println, _logs += _) // don't log stdout
    val process = processBuilder run processLogger

    // Set up a Future to wait for (and signal) the test process exit.
    val exitValue: Future[Int] = Future {
      blocking {
        process.exitValue
      }
    }
    // Wait for the startup message
    // NOTE: There may be several messages before we see our startup message.
    val simStartupMessageStart = "sim start on "
    while (!_logs.exists(_ startsWith simStartupMessageStart) && !exitValue.isCompleted) { Thread.sleep(100) }
    // Remove the startup message (and any precursors).
    while (!_logs.isEmpty && !_logs.head.startsWith(simStartupMessageStart)) {
      println(_logs.remove(0))
    }
    if (!_logs.isEmpty) println(_logs.remove(0)) else println("<no startup message>")
    while (_logs.size < 3) {
      // If the test application died, throw a run-time error.
      throwExceptionIfDead(exitValue)
      Thread.sleep(100)
    }
    val in_channel_name = _logs.remove(0)
    val out_channel_name = _logs.remove(0)
    val cmd_channel_name = _logs.remove(0)
    val in_channel = new Channel(in_channel_name)
    val out_channel = new Channel(out_channel_name)
    val cmd_channel = new Channel(cmd_channel_name)

    println(s"inChannelName: ${in_channel_name}")
    println(s"outChannelName: ${out_channel_name}")
    println(s"cmdChannelName: ${cmd_channel_name}")

    in_channel.consume
    cmd_channel.consume
    in_channel.release
    out_channel.release
    cmd_channel.release
    simTime = 0

    (process, exitValue, in_channel, out_channel, cmd_channel)
  }
  /*constructor logic*/
}

case class TestApplicationException(exitVal: Int, lastMessage: String) extends RuntimeException(lastMessage)

class Channel(name: String) {
  private lazy val file = new java.io.RandomAccessFile(name, "rw")
  private lazy val channel = file.getChannel
  @volatile private lazy val buffer = {
    /* We have seen runs where buffer.put(0,0) fails with:
[info]   java.lang.IndexOutOfBoundsException:
[info]   at java.nio.Buffer.checkIndex(Buffer.java:532)
[info]   at java.nio.DirectByteBuffer.put(DirectByteBuffer.java:300)
[info]   at Chisel.Tester$Channel.release(Tester.scala:148)
[info]   at Chisel.Tester.start(Tester.scala:717)
[info]   at Chisel.Tester.<init>(Tester.scala:743)
[info]   at ArbiterSuite$ArbiterTests$8.<init>(ArbiterTest.scala:396)
[info]   at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]   at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]   at Chisel.Driver$.apply(Driver.scala:65)
[info]   at Chisel.chiselMain$.apply(hcl.scala:63)
[info]   ...
     */
    val size = channel.size
    assert(size > 16, "channel.size is bogus: %d".format(size))
    channel map (FileChannel.MapMode.READ_WRITE, 0, size)
  }
  implicit def intToByte(i: Int) = i.toByte
  val channel_data_offset_64bw = 4    // Offset from start of channel buffer to actual user data in 64bit words.
  def aquire {
    buffer put (0, 1)
    buffer put (2, 0)
    while((buffer get 1) == 1 && (buffer get 2) == 0) {}
  }
  def release { buffer put (0, 0) }
  def ready = (buffer get 3) == 0
  def valid = (buffer get 3) == 1
  def produce { buffer put (3, 1) }
  def consume { buffer put (3, 0) }
  def update(idx: Int, data: Long) { buffer putLong (8 * idx + channel_data_offset_64bw, data) }
  def update(base: Int, data: String) {
    data.zipWithIndex foreach {case (c, i) => buffer put (base + i + channel_data_offset_64bw, c) }
    buffer put (base + data.size + channel_data_offset_64bw, 0)
  }
  def apply(idx: Int): Long = buffer getLong (8 * idx + channel_data_offset_64bw)
  def close { file.close }
  buffer order java.nio.ByteOrder.nativeOrder
  new java.io.File(name).delete
}
