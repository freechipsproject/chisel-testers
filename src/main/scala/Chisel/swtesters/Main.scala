package Chisel.swtesters

import Chisel._
import java.io.{File, IOException, PrintWriter}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import scala.sys.process.stringSeqToProcess
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.collection.immutable.ListMap

object chiselMainTest {
  private var isVCS = false
  private var isGenHarness = false
  private var isCompiling = false
  protected[swtesters] var testerSeed = System.currentTimeMillis
  protected[swtesters] val testCmd = ArrayBuffer[String]()

  private def parseArgs(args: Array[String]) {
    for (i <- 0 until args.size) {
      args(i) match {
        case "--vcs" => isVCS = true
        case "--genHarness" => isGenHarness = true
        case "--compile" => isCompiling = true
        case "--testCommand" => testCmd ++= args(i+1) split ' '
        case _ =>
      }
    }
  }

  private def compile(circuit: internal.firrtl.Circuit) {
    // Copy API files
    val simApiHFilePath = Paths.get(s"${Driver.targetDir}/sim_api.h")
    val veriApiHFilePath = Paths.get(s"${Driver.targetDir}/veri_api.h")
    try {
      Files.createDirectory(Paths.get(Driver.targetDir))
      Files.createFile(simApiHFilePath)
      Files.createFile(veriApiHFilePath)
    } catch {
      case x: FileAlreadyExistsException =>
      case x: IOException =>
        System.err.format("createFile error: %s%n", x)
    }

    Files.copy(getClass.getResourceAsStream("/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/veri_api.h"), veriApiHFilePath, REPLACE_EXISTING)

    val dir = new File(Driver.targetDir)
    val firrtl = new File(s"${Driver.targetDir}/${circuit.name}.fir")
    // Generate FIRRTL
    Driver.dumpFirrtl(circuit, Some(firrtl))
    // Generate Verilog
    Driver.firrtlToVerilog(circuit.name, dir).!

    if (isVCS) {
    } else {
      // Generate Verilator
      val harness = new File(s"${dir}/${circuit.name}-harness.cpp")
      Driver.verilogToCpp(circuit.name, dir, Seq(), harness).!
      // Compile Verilator
      Driver.cppToExe(circuit.name, dir).!
    }
  }

  def apply[T <: Module](args: Array[String], dutGenFunc: () => T)(testerGenFunc: T => ClassicTester) {
    parseArgs(args)
    Driver.parseArgs(args)
    lazy val dut = dutGenFunc()
    val circuit = Driver.elaborate(() => dut)
    if (isGenHarness) genHarness(dut, circuit.name, isVCS)
    if (isCompiling) compile(circuit)
    // Run Classic Tester
    if (testCmd.isEmpty) { 
      testCmd += s"""${Driver.targetDir}/${if (isVCS) "" else "V"}${circuit.name}"""
    }
    assert(testerGenFunc(dut).finish, "Test failed")
  }
}

protected[swtesters] object parsePorts {
  def apply(io: Data) = {
    val inputMap = new LinkedHashMap[Bits, (String, Int)]()
    val outputMap = new LinkedHashMap[Bits, (String, Int)]()

    def loop(name: String, data: Data): Unit = data match {
      case b: Bundle => b.elements foreach {case (n, e) => loop(s"${name}_${n}", e)}
      case v: Vec[_] => v.zipWithIndex foreach {case (e, i) => loop(s"${name}_${i}", e)}
      case b: Bits if b.dir == INPUT => inputMap(b) = (name, b.getWidth)
      case b: Bits if b.dir == OUTPUT => outputMap(b) = (name, b.getWidth)
      case _ => // skip
    }

    loop("io", io)
    (inputMap, outputMap)
    (ListMap(inputMap.toSeq:_*), ListMap(outputMap.toSeq:_*))
  }
}

protected[swtesters] object genHarness {
  def apply[T <: Module](dut: T, dutName: String, isVCS: Boolean) {
    val (inputMap, outputMap) = parsePorts(dut.io)
    val inputs = inputMap.toList.unzip._2
    val outputs = outputMap.toList.unzip._2
    if (isVCS) {
    } else {
      genCppHarness(dutName, inputs, outputs)
    }
  }

  private def genCppHarness(dutName: String, inputs: List[(String, Int)], outputs: List[(String, Int)]) {
    val dutApiClassName = s"${dutName}_api_t"
    val dutVerilatorClassName = s"V${dutName}"
    val cppHarnessFilePath = s"${Driver.targetDir}/${dutName}-harness.cpp"
 
    val fileWriter = new PrintWriter(new File(cppHarnessFilePath))

    fileWriter.write("#include \"%s.h\"\n".format(dutVerilatorClassName))
    fileWriter.write("#include \"verilated.h\"\n")
    fileWriter.write("#include \"veri_api.h\"\n")
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
    inputs foreach { case (nodeName, nodeWidth) =>
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
    outputs foreach { case (nodeName, nodeWidth) =>
      if (nodeWidth <= 8) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 16) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 32) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 64) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (nodeWidth-1)/32 + 1
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
