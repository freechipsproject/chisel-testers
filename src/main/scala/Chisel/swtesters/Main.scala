// See LICENSE for license details.

package Chisel.swtesters

import Chisel._
import java.io.{File, IOException, PrintWriter}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import scala.sys.process.stringSeqToProcess
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.collection.immutable.ListMap
import scala.util.DynamicVariable

private[swtesters] class TesterContext {
  var isVCS = false
  var isGenHarness = false
  var isCompiling = false
  var testerSeed = System.currentTimeMillis
  val testCmd = ArrayBuffer[String]()
  val inputMap = LinkedHashMap[Bits, (String, Int)]()
  val outputMap = LinkedHashMap[Bits, (String, Int)]()
}

object chiselMain {
  private val contextVar = new DynamicVariable[Option[TesterContext]](None)
  private[swtesters] def context = contextVar.value getOrElse (new TesterContext)

  private def parseArgs(args: Array[String]) {
    for (i <- 0 until args.size) {
      args(i) match {
        case "--vcs" => context.isVCS = true
        case "--genHarness" => context.isGenHarness = true
        case "--compile" => context.isCompiling = true
        case "--testCommand" => context.testCmd ++= args(i+1) split ' '
        case _ =>
      }
    }
  }

  private def parsePorts(io: Data) {
    def loop(name: String, data: Data): Unit = data match {
      case b: Bundle => b.elements foreach {case (n, e) => loop(s"${name}_${n}", e)}
      case v: Vec[_] => v.zipWithIndex foreach {case (e, i) => loop(s"${name}_${i}", e)}
      case b: Bits if b.dir == INPUT => context.inputMap(b) = (name, b.getWidth)
      case b: Bits if b.dir == OUTPUT => context.outputMap(b) = (name, b.getWidth)
      case _ => // skip
    }
    loop("io", io)
  }

  private def compile(circuit: internal.firrtl.Circuit) {
    // Copy API files
    val simApiHFilePath = Paths.get(s"${Driver.targetDir}/sim_api.h")
    val veriApiHFilePath = Paths.get(s"${Driver.targetDir}/veri_api.h")
    try {
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
    // Dump FIRRTL for debugging
    Driver.dumpFirrtl(circuit, Some(new File(s"${dir}/${circuit.name}.ir")))
    // Parse FIRRTL
    val ir = firrtl.Parser.parse(circuit.emit split "\n")
    // Generate Verilog
    val v = new PrintWriter(new File(s"${dir}/${circuit.name}.v"))
    firrtl.VerilogCompiler.run(ir, v)
    v.close

    if (context.isVCS) {
    } else {
      // Generate Verilator
      val harness = new File(s"${dir}/${circuit.name}-harness.cpp")
      Driver.verilogToCpp(circuit.name, dir, Seq(), harness).!
      // Compile Verilator
      Driver.cppToExe(circuit.name, dir).!
    }
  }

  private def elaborate[T <: Module](args: Array[String], dutGen: () => T): T = {
    parseArgs(args)
    Driver.parseArgs(args)
    try {
      Files.createDirectory(Paths.get(Driver.targetDir))
    } catch {
      case x: FileAlreadyExistsException =>
      case x: IOException =>
        System.err.format("createFile error: %s%n", x)
    }
    lazy val dut = dutGen()
    val circuit = Driver.elaborate(() => dut)
    parsePorts(dut.io)
    if (context.isGenHarness) genHarness(dut, circuit.name, context.isVCS)
    if (context.isCompiling) compile(circuit)
    if (context.testCmd.isEmpty) {
      context.testCmd += s"""${Driver.targetDir}/${if (context.isVCS) "" else "V"}${dut.name}"""
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

  def apply[T <: Module](args: Array[String], dutGen: () => T, testerGen: T => ClassicTester[T]): T = {
    contextVar.withValue(Some(new TesterContext)) {
      val dut = elaborate(args, dutGen)
      assert(testerGen(dut).finish, "Test failed")
      dut
    }
  }
}

object chiselMainTest {
  def apply[T <: Module](args: Array[String], dutGen: () => T)(testerGen: T => ClassicTester[T]) = {
    chiselMain(args, dutGen, testerGen)
  }
}

private[swtesters] object genHarness {
  def apply[T <: Module](dut: T, dutName: String, isVCS: Boolean) {
    val inputs = chiselMain.context.inputMap.toList.unzip._2
    val outputs = chiselMain.context.outputMap.toList.unzip._2
    if (isVCS) {
    } else {
      genCppHarness(dutName, inputs, outputs)
    }
  }

  private def genCppHarness(dutName: String, inputs: List[(String, Int)], outputs: List[(String, Int)]) {
    val dutApiClassName = s"${dutName}_api_t"
    val dutVerilatorClassName = s"V${dutName}"
    val cppHarnessFilePath = s"${Driver.targetDir}/${dutName}-harness.cpp"
    val vcdFilePath = s"${Driver.targetDir}/${dutName}.vcd"
 
    val fileWriter = new PrintWriter(new File(cppHarnessFilePath))

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
