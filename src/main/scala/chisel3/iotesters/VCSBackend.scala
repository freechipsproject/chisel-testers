// See LICENSE for license details.
package chisel3.iotesters

import chisel3.internal.HasId

import scala.collection.mutable.HashMap
import scala.util.Random
import java.io.{File, Writer, FileWriter, PrintStream, IOException}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
  * Copies the necessary header files used for verilator compilation to the specified destination folder
  */
object copyVpiFiles {
  def apply(destinationDirPath: String): Unit = {
    new File(destinationDirPath).mkdirs()
    val simApiHFilePath = Paths.get(destinationDirPath + "/sim_api.h")
    val vpiHFilePath = Paths.get(destinationDirPath + "/vpi.h")
    val vpiCppFilePath = Paths.get(destinationDirPath + "/vpi.cpp")
    val vpiTabFilePath = Paths.get(destinationDirPath + "/vpi.tab")
    try {
      Files.createFile(simApiHFilePath)
      Files.createFile(vpiHFilePath)
      Files.createFile(vpiCppFilePath)
      Files.createFile(vpiTabFilePath)
    } catch {
      case x: FileAlreadyExistsException =>
        System.out.format("")
      case x: IOException => {
        System.err.format("createFile error: %s%n", x)
      }
    }

    Files.copy(getClass.getResourceAsStream("/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/vpi.h"), vpiHFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/vpi.cpp"), vpiCppFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/vpi.tab"), vpiTabFilePath, REPLACE_EXISTING)
  }
}

/**
  * Generates the Module specific verilator harness cpp file for verilator compilation
  */
object genVCSVerilogHarness {
  def apply(dut: chisel3.Module, writer: Writer, vpdFilePath: String) {
    val dutName = dut.name
    val (inputs, outputs) = getDataNames(dut) partition (_._1.dir == chisel3.INPUT)

    writer write "module test;\n"
    writer write "  reg clk = 1;\n"
    writer write "  reg rst = 1;\n"
    val delay = if (chiselMain.context.isPropagation) "" else "#0.1"
    inputs foreach { case (node, name) =>
      writer write s"  reg[${node.getWidth-1}:0] $name = 0;\n"
      writer write s"  wire[${node.getWidth-1}:0] ${name}_delay;\n"
      writer write s"  assign $delay ${name}_delay = $name;\n"
    }
    outputs foreach { case (node, name) =>
      writer write s"  wire[${node.getWidth-1}:0] ${name}_delay;\n"
      writer write s"  wire[${node.getWidth-1}:0] $name;\n"
      writer write s"  assign $delay $name = ${name}_delay;\n"
    }

    writer write "  always #`CLOCK_PERIOD clk = ~clk;\n"
    writer write "  reg vcdon = 0;\n"
    writer write "  reg [1023:0] vcdfile = 0;\n"
    writer write "  reg [1023:0] vpdfile = 0;\n"

    writer write "\n  /*** DUT instantiation ***/\n"
    writer write s"  ${dutName} ${dutName}(\n"
    writer write "    .clk(clk),\n"
    writer write "    .reset(rst),\n"
    writer write ((inputs ++ outputs).unzip._2 map (name => s"    .${name}(${name}_delay)") mkString ",\n")
    writer write "  );\n\n"

    writer write "  initial begin\n"
    writer write "    $init_rsts(rst);\n"
    writer write "    $init_ins(%s);\n".format(inputs.unzip._2 mkString ", ")
    writer write "    $init_outs(%s);\n".format(outputs.unzip._2 mkString ", ")
    writer write "    $init_sigs(%s);\n".format(dutName)
    writer write "    /*** VCD & VPD dump ***/\n"
    writer write "    if ($value$plusargs(\"vcdfile=%s\", vcdfile)) begin\n"
    writer write "      $dumpfile(vcdfile);\n"
    writer write "      $dumpvars(0, %s);\n".format(dutName)
    writer write "      $dumpoff;\n"
    writer write "      vcdon = 0;\n"
    writer write "    end\n"
    writer write "    if ($value$plusargs(\"waveform=%s\", vpdfile)) begin\n"
    writer write "      $vcdplusfile(vpdfile);\n"
    writer write "    end else begin\n"
    writer write "      $vcdplusfile(\"%s\");\n".format(vpdFilePath)
    writer write "    end\n"
    writer write "    if ($test$plusargs(\"vpdmem\")) begin\n"
    writer write "      $vcdplusmemon;\n"
    writer write "    end\n"
    writer write "    $vcdpluson(0);\n"
    writer write "    $vcdplusautoflushon;\n"
    writer write "  end\n\n"

    writer write "  always @(%s clk) begin\n".format(if (chiselMain.context.isPropagation) "negedge" else "posedge")
    writer write "    if (vcdfile && rst) begin\n"
    writer write "      $dumpoff;\n"
    writer write "      vcdon = 0;\n"
    writer write "    end\n"
    writer write "    else if (vcdfile && !vcdon) begin\n"
    writer write "      $dumpon;\n"
    writer write "      vcdon = 1;\n"
    writer write "    end\n"
    writer write "    $tick();\n"
    writer write "  end\n\n"
    writer write "endmodule\n"
    writer.close
  }
}

private[iotesters] object setupVCSBackend {
  def apply(dutGen: () => chisel3.Module): Backend = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val dir = new File(testDirPath)
    dir.mkdirs()

    CircuitGraph.clear
    val circuit = chisel3.Driver.elaborate(dutGen)
    val dut = CircuitGraph construct circuit

    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${testDirPath}/${circuit.name}.ir"
    chisel3.Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    // Generate Verilog
    val verilogFilePath = s"${testDirPath}/${circuit.name}.v"
    firrtl.Driver.compile(firrtlIRFilePath, verilogFilePath, new firrtl.VerilogCompiler)

    val verilogFileName = verilogFilePath.split("/").last
    val vcsHarnessFileName = "classic_tester_top.v"
    val vcsHarnessFilePath = s"${testDirPath}/${vcsHarnessFileName}"
    val vcsBinaryPath = s"${testDirPath}/V${circuit.name}"
    val vpdFilePath = s"${testDirPath}/${circuit.name}.vpd"

    // Generate Harness
    copyVpiFiles(testDirPath)
    genVCSVerilogHarness(dut, new FileWriter(new File(vcsHarnessFilePath)), vpdFilePath)
    verilogToVCS(dut.name, new File(testDirPath), new File(vcsHarnessFileName)).!

    new VCSBackend(dut, List(vcsBinaryPath))
  }
}

private[iotesters] class VCSBackend(
                                    dut: chisel3.Module, 
                                    cmd: List[String],
                                    verbose: Boolean = true,
                                    logger: PrintStream = System.out,
                                    _base: Int = 16,
                                    _seed: Long = System.currentTimeMillis) 
           extends VerilatorBackend(dut, cmd, verbose, logger, _base, _seed)
