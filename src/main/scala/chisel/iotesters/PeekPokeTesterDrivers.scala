// See LICENSE for license details.

package chisel.iotesters

import chisel._

import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.io.{File, IOException, PrintWriter}

/**
  * Runs the ClassicTester and returns a Boolean indicating test success or failure
  * @@backendType determines whether the ClassicTester uses verilator or the firrtl interpreter to simulate the circuit
  * Will do intermediate compliation steps to setup the backend specified, including cpp compilation for the verilator backend and firrtl IR compilation for the firrlt backend
  */
object runPeekPokeTester {
  def apply[T <: Module](dutGen: () => T, backendType: String = "firrtl")(testerGen: (T, Option[Backend]) => PeekPokeTester[T]): Boolean = {
    var backend: Backend = null
    if (backendType == "verilator") {
      backend = setupVerilatorBackend(dutGen)
    } else if (backendType == "firrtl") {
      backend = setupFirrtlTerpBackend(dutGen)
    } else {
      assert(false, "Unrecongnized backend type:" + backendType)
    }
    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)
    testerGen(dut, Some(backend)).finish
  }
}

/**
  * Copies the necessary header files used for verilator compilation to the specified destination folder
  */
object copyVerilatorHeaderFiles {
  def apply(destinationDirPath: String): Unit = {
    new File(destinationDirPath).mkdirs()
    val simApiHFilePath = Paths.get(destinationDirPath + "/sim_api.h")
    val verilatorApiHFilePath = Paths.get(destinationDirPath + "/veri_api.h")
    try {
      Files.createFile(simApiHFilePath)
      Files.createFile(verilatorApiHFilePath)
    } catch {
      case x: FileAlreadyExistsException =>
        System.out.format("")
      case x: IOException => {
        System.err.format("createFile error: %s%n", x)
      }
    }

    Files.copy(getClass.getResourceAsStream("/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/veri_api.h"), verilatorApiHFilePath, REPLACE_EXISTING)
  }
}

/**
  * Generates the Module specific verilator harness cpp file for verilator compilation
  */
object genVerilatorCppHarness {
  def apply(dutGen: () => Module, verilogFileName: String, cppHarnessFilePath: String, vcdFilePath: String): Unit = {
    def getVerilatorName(arg: (Bits, (String, String))) = arg match {
      case (io, (name, _)) => io -> name
    }
    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)
    val (dutInputNodeInfo, dutOutputNodeInfo) = getPortNameMaps(dut)
    val (inputs, outputs) = (dutInputNodeInfo.toList map getVerilatorName, dutOutputNodeInfo.toList map getVerilatorName)
    val dutName = verilogFileName.split("\\.")(0)
    val dutApiClassName = dutName + "_api_t"
    val dutVerilatorClassName = "V" + dutName
    val cppHarnessFile = new File(cppHarnessFilePath)
    cppHarnessFile.getParentFile.mkdirs()
    val fileWriter = new PrintWriter(cppHarnessFile)

    fileWriter.write("#include \"%s.h\"\n".format(dutVerilatorClassName))
    fileWriter.write("#include \"verilated.h\"\n")
    fileWriter.write("#include \"veri_api.h\"\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("#include \"verilated_vcd_c.h\"\n")
    fileWriter.write("#endif\n")
    fileWriter.write("#include <iostream>\n")

    fileWriter.write(s"class ${dutApiClassName}: public sim_api_t<VerilatorDataWrapper*> {\n")
    fileWriter.write("public:\n")
    fileWriter.write(s"    ${dutApiClassName}(${dutVerilatorClassName}* _dut) {\n")
    fileWriter.write("        dut = _dut;\n")
    fileWriter.write("        main_time = 0L;\n")
    fileWriter.write("        is_exit = false;\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("        tfp = NULL;\n")
    fileWriter.write("#endif\n")
    fileWriter.write("    }\n")
    fileWriter.write("    void init_sim_data() {\n")
    fileWriter.write("        sim_data.inputs.clear();\n")
    fileWriter.write("        sim_data.outputs.clear();\n")
    fileWriter.write("        sim_data.signals.clear();\n")
    inputs foreach { case (node, nodeName) =>
      if (node.getWidth <= 8) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 16) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 32) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 64) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (node.getWidth - 1)/32 + 1
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    outputs foreach { case (node, nodeName) =>
      if (node.getWidth <= 8) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 16) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 32) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (node.getWidth <= 64) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (node.getWidth-1)/32 + 1
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    fileWriter.write("    }\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("     void init_dump(VerilatedVcdC* _tfp) { tfp = _tfp; }\n")
    fileWriter.write("#endif\n")
    fileWriter.write("    inline bool exit() { return is_exit; }\n")
    fileWriter.write("private:\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* dut;\n")
    fileWriter.write("    bool is_exit;\n")
    fileWriter.write("    vluint64_t main_time;\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("    VerilatedVcdC* tfp;\n")
    fileWriter.write("#endif\n")
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
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("        if (tfp) tfp->dump(main_time);\n")
    fileWriter.write("#endif\n")
    fileWriter.write("        main_time++;\n")
    fileWriter.write("        dut->clk = 1;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("        if (tfp) tfp->dump(main_time);\n")
    fileWriter.write("#endif\n")
    fileWriter.write("        dut->clk = 0;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        main_time++;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void update() {\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("    }\n")
    fileWriter.write("};\n")
    fileWriter.write("int main(int argc, char **argv, char **env) {\n")
    fileWriter.write("    Verilated::commandArgs(argc, argv);\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* top = new ${dutVerilatorClassName};\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("    Verilated::traceEverOn(true);\n")
    fileWriter.write("    VL_PRINTF(\"Enabling waves..\");\n")
    fileWriter.write("    VerilatedVcdC* tfp = new VerilatedVcdC;\n")
    fileWriter.write("    top->trace(tfp, 99);\n")
    fileWriter.write("    tfp->open(\"%s\");\n".format(vcdFilePath))
    fileWriter.write("#endif\n")
    fileWriter.write(s"    ${dutApiClassName} api(top);\n")
    fileWriter.write("    api.init_sim_data();\n")
    fileWriter.write("    api.init_channels();\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("    api.init_dump(tfp);\n")
    fileWriter.write("#endif\n")
    fileWriter.write("    while(!api.exit()) api.tick();\n")
    fileWriter.write("#if VM_TRACE\n")
    fileWriter.write("    if (tfp) tfp->close();\n")
    fileWriter.write("    delete tfp;\n")
    fileWriter.write("#endif\n")
    fileWriter.write("    delete top;\n")
    fileWriter.write("    exit(0);\n")
    fileWriter.write("}\n")
    fileWriter.close()
    println(s"ClassicTester CppHarness generated at ${cppHarnessFilePath}")
  }
}

/**
  * Runs the ClassicTester using the verilator backend without doing Verilator compilation and returns a Boolean indicating success or failure
  * Requires the caller to supply path the already compile Verilator binary
  */
object runPeekPokeTesterWithVerilatorBinary {
  def apply[T <: Module] (dutGen: () => T, verilatorBinaryFilePath: String)
                         (testerGen: (T, Option[Backend]) => PeekPokeTester[T]): Boolean = {
    lazy val dut = dutGen() //HACK to get Module instance for now; DO NOT copy
    Driver.elaborate(() => dut)
    val tester = testerGen(dut, Some(new VerilatorBackend(dut, verilatorBinaryFilePath)))
    tester.finish
  }
}

