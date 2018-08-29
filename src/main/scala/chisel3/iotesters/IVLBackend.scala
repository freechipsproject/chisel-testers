//// See LICENSE for license details.
//package chisel3.iotesters
//
//import java.io.{File, FileWriter, IOException, Writer}
//import java.nio.file.{FileAlreadyExistsException, Files, Paths}
//import java.nio.file.StandardCopyOption.REPLACE_EXISTING
//
//import chisel3.{ChiselExecutionFailure, ChiselExecutionSuccess, Element}
//import chisel3.experimental.MultiIOModule
//import firrtl.{ChirrtlForm, CircuitState}
//import firrtl.transforms.BlackBoxTargetDirAnno
//
///**
//  * Copies the necessary header files used for iverilog compilation to the specified destination folder
//  */
//object copyIvlFiles {
//  def apply(destinationDirPath: String): Unit = {
//    new File(destinationDirPath).mkdirs()
//    val simApiHFilePath = Paths.get(destinationDirPath + "/sim_api.h")
//    val vpiHFilePath = Paths.get(destinationDirPath + "/vpi.h")
//    val vpiCppFilePath = Paths.get(destinationDirPath + "/vpi.cpp")
//    val vpiRegFilePath = Paths.get(destinationDirPath + "/vpi_register.cpp")
//    try {
//      Files.createFile(simApiHFilePath)
//      Files.createFile(vpiHFilePath)
//      Files.createFile(vpiCppFilePath)
//      Files.createFile(vpiRegFilePath)
//    } catch {
//      case _: FileAlreadyExistsException =>
//        System.out.format("")
//      case x: IOException =>
//        System.err.format("createFile error: %s%n", x)
//    }
//
//    Files.copy(getClass.getResourceAsStream("/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
//    Files.copy(getClass.getResourceAsStream("/vpi.h"), vpiHFilePath, REPLACE_EXISTING)
//    Files.copy(getClass.getResourceAsStream("/vpi.cpp"), vpiCppFilePath, REPLACE_EXISTING)
//    Files.copy(getClass.getResourceAsStream("/vpi_register.cpp"), vpiRegFilePath, REPLACE_EXISTING)
//  }
//}
//
///**
//  * Generates the Module specific verilator harness cpp file for verilator compilation
//  */
//object genIVLVerilogHarness {
//  def apply(dut: MultiIOModule, writer: Writer, vpdFilePath: String, isGateLevel: Boolean = false) {
//    val dutName = dut.name
//    // getPorts() is going to return names prefixed with the dut name.
//    // These don't correspond to code currently generated for verilog modules,
//    //  so we have to strip the dut name prefix (and the delimiter) from the name.
//    // We tell getPorts() to use "_" as the delimiter to simplify the replacement regexp.
//    def fixnames(portSeq: (Seq[(Element, String)], Seq[(Element, String)])): (Seq[(Element, String)], Seq[(Element, String)]) = {
//      val replaceRegexp = ("^" + dutName + "_").r
//      (
//        portSeq._1 map { case (e: Element, s: String) => (e, replaceRegexp.replaceFirstIn(s, ""))},
//        portSeq._2 map { case (e: Element, s: String) => (e, replaceRegexp.replaceFirstIn(s, ""))}
//      )
//    }
//    val (inputs, outputs) = fixnames(getPorts(dut, "_"))
//
//    writer write "module test;\n"
//    writer write "  reg clock = 1;\n"
//    writer write "  reg reset = 1;\n"
//    val delay = if (isGateLevel) "#0.1" else ""
//    inputs foreach { case (node, name) =>
//      if ("clock" != name && "reset" != name) {
//        writer write s"  reg[${node.getWidth-1}:0] $name = 0;\n"
//        writer write s"  wire[${node.getWidth-1}:0] ${name}_delay;\n"
//        writer write s"  assign $delay ${name}_delay = $name;\n"
//      }
//    }
//    outputs foreach { case (node, name) =>
//      writer write s"  wire[${node.getWidth-1}:0] ${name}_delay;\n"
//      writer write s"  wire[${node.getWidth-1}:0] $name;\n"
//      writer write s"  assign $delay $name = ${name}_delay;\n"
//    }
//
//    writer write "  always #`CLOCK_PERIOD clock = ~clock;\n"
//    writer write "  reg vcdon = 0;\n"
//    writer write "  reg [1023:0] vcdfile = 0;\n"
//    writer write "  reg [1023:0] vpdfile = 0;\n"
//
//    writer write "\n  /*** DUT instantiation ***/\n"
//    writer write s"  $dutName $dutName(\n"
//    writer write "    .clock(clock),\n"
//    writer write "    .reset(reset),\n"
//    writer write (((inputs ++ outputs).unzip._2 map (name =>
//        if ("clock" != name && "reset" != name) s"    .$name(${name}_delay)" else None)).filter(_ != None) mkString ",\n"
//      )
//    writer write "  );\n\n"
//
//    writer write "  initial begin\n"
//    writer write "    $init_rsts(reset);\n"
//    writer write "    $init_ins(%s);\n".format(inputs.unzip._2 mkString ", ")
//    writer write "    $init_outs(%s);\n".format(outputs.unzip._2 mkString ", ")
//    writer write "    $init_sigs(%s);\n".format(dutName)
//    writer write "    /*** VCD dump ***/\n"
//    writer write "    if ($value$plusargs(\"vcdfile=%s\", vcdfile)) begin\n"
//    writer write "      $dumpfile(vcdfile);\n"
//    writer write "      $dumpvars(0, %s);\n".format(dutName)
//    writer write "      $dumpoff;\n"
//    writer write "      vcdon = 0;\n"
//    writer write "    end\n"
//    writer write "  end\n\n"
//
//    writer write "  always @(%s clock) begin\n".format(if (isGateLevel) "posedge" else "negedge")
//    writer write "    if (vcdfile && reset) begin\n"
//    writer write "      $dumpoff;\n"
//    writer write "      vcdon = 0;\n"
//    writer write "    end\n"
//    writer write "    else if (vcdfile && !vcdon) begin\n"
//    writer write "      $dumpon;\n"
//    writer write "      vcdon = 1;\n"
//    writer write "    end\n"
//    writer write "    %s $tick();\n".format(if (isGateLevel) "#0.05" else "")
//    writer write "  end\n\n"
//    writer write "endmodule\n"
//    writer.close()
//  }
//}
//
//private[iotesters] object setupIVLBackend {
//  def apply[T <: MultiIOModule](dutGen: () => T, optionsManager: TesterOptionsManager): (T, Backend) = {
//    optionsManager.makeTargetDir()
//    optionsManager.chiselOptions = optionsManager.chiselOptions.copy(
//      runFirrtlCompiler = false
//    )
//    val dir = new File(optionsManager.targetDirName)
//
//    // Generate CHIRRTL
//    chisel3.Driver.execute(optionsManager, dutGen) match {
//      case ChiselExecutionSuccess(Some(circuit), emitted, _) =>
//
//        val chirrtl = firrtl.Parser.parse(emitted)
//        val dut = getTopModule(circuit).asInstanceOf[T]
//
//        /*
//        The following block adds an annotation that tells the black box helper where the
//        current build directory is, so that it can copy verilog resource files into the right place
//         */
//        val annotations = optionsManager.firrtlOptions.annotations ++
//          List(BlackBoxTargetDirAnno(optionsManager.targetDirName))
//
//        val transforms = optionsManager.firrtlOptions.customTransforms
//
//        // Generate Verilog
//        val verilogFile = new File(dir, s"${circuit.name}.v")
//        val verilogWriter = new FileWriter(verilogFile)
//
//        val compileResult = (new firrtl.VerilogCompiler).compileAndEmit(
//          CircuitState(chirrtl, ChirrtlForm, annotations),
//          customTransforms = transforms
//        )
//        val compiledStuff = compileResult.getEmittedCircuit
//        verilogWriter.write(compiledStuff.value)
//        verilogWriter.close()
//
//        // Generate Harness
//        val ivlHarnessFileName = s"${circuit.name}-harness.v"
//        val ivlHarnessFile = new File(dir, ivlHarnessFileName)
//        val vcdFile = new File(dir, s"${circuit.name}.vcd")
//        copyIvlFiles(dir.toString)
//        genIVLVerilogHarness(dut, new FileWriter(ivlHarnessFile), vcdFile.toString)
//        assert(
//          verilogToIVL(circuit.name, dir, new File(ivlHarnessFileName),
//            moreIvlFlags = optionsManager.testerOptions.moreIvlFlags,
//            moreIvlCFlags = optionsManager.testerOptions.moreIvlCFlags,
//            editCommands = optionsManager.testerOptions.ivlCommandEdits
//          ).! == 0)
//
//        val command = if(optionsManager.testerOptions.testCmd.nonEmpty) {
//          optionsManager.testerOptions.testCmd
//        }
//        else {
//          Seq(new File(dir, circuit.name).toString)
//        }
//
//        (dut, new IVLBackend(dut, command))
//      case ChiselExecutionFailure(message) =>
//        throw new Exception(message)
//    }
//  }
//}
//
//private[iotesters] class IVLBackend(dut: MultiIOModule,
//                                    cmd: Seq[String],
//                                    _seed: Long = System.currentTimeMillis)
//           extends VerilatorBackend(dut, cmd, _seed)
