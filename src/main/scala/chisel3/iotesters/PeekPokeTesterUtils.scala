// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import java.io.File

import chisel3._
import chisel3.experimental._
import chisel3.internal.InstanceId
import chisel3.internal.firrtl.Circuit

import scala.sys.process._
import scala.collection.mutable.ArrayBuffer

// TODO: FIRRTL will eventually return valid names
private[iotesters] object validName {
  def apply(name: String): String = (if (firrtl.Utils.v_keywords contains name) name + "$"
    else name) replace (".", "_") replace ("[", "_") replace ("]", "")
}

private[iotesters] object getDataNames {
  def apply(name: String, data: Data): Seq[(Element, String)] = data match {
    case e: Element => Seq(e -> name)
    case b: Record => b.elements.toSeq flatMap {case (n, e) => apply(s"${name}_$n", e)}
    case v: Vec[_] => v.zipWithIndex flatMap {case (e, i) => apply(s"${name}_$i", e)}
  }
  def apply(dut: Module, separator: String = "."): Seq[(Element, String)] =
    dut.getPorts.flatMap { case chisel3.internal.firrtl.Port(data, _) =>
      apply(data.pathName replace (".", separator), data)
    }

}

private[iotesters] object getPorts {
  def apply(dut: Module, separator: String = "."): (Seq[(Element, String)], Seq[(Element, String)]) =
    getDataNames(dut, separator) partition { case (e, _) => DataMirror.directionOf(e) == ActualDirection.Input }
}

private[iotesters] object flatten {
  def apply(data: Data): Seq[Element] = data match {
    case b: Element => Seq(b)
    case b: Record => b.elements.toSeq flatMap (x => apply(x._2))
    case v: Vec[_] => v flatMap apply
  }
}

private[iotesters] object getTopModule {
  def apply(circuit: Circuit): BaseModule = {
    (circuit.components find (_.name == circuit.name)).get.id
  }
}

/* TODO: Chisel should provide nodes of the circuit? */
private[iotesters] object getChiselNodes {
  import chisel3.internal.firrtl._
  def apply(circuit: Circuit): Seq[InstanceId] = {
    circuit.components flatMap {
      case m: DefModule =>
        m.commands flatMap {
          case x: DefReg => flatten(x.id)
          case mem: DefMemory => mem.t match {
            case _: Element => Seq(mem.id)
            case _ => Nil // Do not support aggregate type memories
          }
          case mem: DefSeqMemory => mem.t match {
            case _: Element => Seq(mem.id)
            case _ => Nil // Do not support aggregate type memories
          }
          case _ => Nil
        }
        // If it's anything else (i.e., a DefBlackBox), we don't know what to do with it.
      case _ => Nil
    } filterNot (x => (x.instanceName slice (0, 2)) == "T_")
  }
}

private[iotesters] object bigIntToStr {
  def apply(x: BigInt, base: Int): String = base match {
    case 2  if x < 0 => s"-0b${(-x).toString(base)}"
    case 16 if x < 0 => s"-0x${(-x).toString(base)}"
    case 2  => s"0b${x.toString(base)}"
    case 16 => s"0x${x.toString(base)}"
    case _ => x.toString(base)
  }
}

/** An EditableBuildCSimulatorCommand provides methods for assembling a system command string from provided flags and editing specifications.
  * This is a trait to facilitate expansion (for more C-based simulators) and testing.
  */
trait EditableBuildCSimulatorCommand {
  val prefix: String  // prefix to be used for error messages

  /** If we have a list of black box verilog implementations, return a sequence suitable for sourcing the file containing the list.
    *
    * @param dir - directory in which the file should exist
    * @return sequence of strings (suitable for passing as arguments to the simulator builder) specifying a flag and the absolute path to the file.
    */
  def blackBoxVerilogList(dir: java.io.File): Seq[String] = {
    val list_file = new File(dir, firrtl.transforms.BlackBoxSourceHelper.fileListName)
    if(list_file.exists()) {
      Seq("-f", list_file.getAbsolutePath)
    } else {
      Seq.empty[String]
    }
  }

  /** Compose user-supplied flags with the default flags.
    * @param topModule - the name of the module to be simulated
    * @param dir - the directory in which to build the simulation
    * @param flags - general flags for the build process
    * @param cFlags - C flags for the build process
    * @return tuple containing a sequence of the composed general flags and a sequence of the composed C flags
    */
  def composeFlags(
                      topModule: String,
                      dir: java.io.File,
                      moreIvlFlags: Seq[String] = Seq.empty[String],
                      moreIvlCFlags: Seq[String] = Seq.empty[String]): (Seq[String], Seq[String])

  /** Given two sets of flags (non-CFlags and CFlags), return the composed command (prior to editting).
    * @param topModule - the name of the module to be simulated
    * @param dir - the directory in which to build the simulation
    * @param flags - general flags for the build process
    * @param cFlags - C flags for the build process
    * @return a string (suitable for "bash -c") to build the simulator.
    */
  def composeCommand(
                        topModule: String,
                        dir: java.io.File,
                        flags: Seq[String],
                        cFlags: Seq[String]
                    ): String

  /** Edit a C simulator build string.
    *
    * @param buildCommand - generated command line to be passed to the build process ("bash -c <cmd>")
    * @param editCommands - commands to edit the generated command line
    * @return edited command string
    */
  def editCSimulatorCommand(
                               buildCommand: String,
                               editCommands: String
                           ): String = {

    val commandEditor = CommandEditor(editCommands, prefix)
    val editedCommand = commandEditor(buildCommand)
    editedCommand
  }

  /** Construct a command to build a C-based simulator.
    *
    * @param topModule - the name of the module to be simulated
    * @param dir - the directory in which to build the simulation
    * @param flags - user flags to be passed to the build process - these will be composed with the default flags for the builder
    * @param cFlags - user C flags to be passed to the build process - these will be composed with the default C flags for the builder
    * @param editCommands - commands to edit the generated command line
    * @return string representing the bash command to be executed
    *
    * @note This method will call `composeFlags()` internally, so the flag parameters should '''NOT''' include the default flags for the builder.
    */
  def constructCSimulatorCommand(
                                         topModule: String,
                                         dir: java.io.File,
                                         harness:  java.io.File,
                                         flags: Seq[String] = Seq.empty[String],
                                         cFlags: Seq[String] = Seq.empty[String]
                                     ): String
}

private[iotesters] object verilogToIVL extends EditableBuildCSimulatorCommand {
  val prefix = "ivl-command-edit"
  def composeCommand(
                      topModule: String,
                      dir: java.io.File,
                      flags: Seq[String],
                      cFlags: Seq[String]
                    ): String = {
    Seq("cd", dir.toString, "&&") ++
      Seq("g++") ++ cFlags ++ Seq("vpi.cpp", "vpi_register.cpp", "&&") ++
      Seq("iverilog") ++ flags mkString " "
  }

  def composeFlags(
               topModule: String,
               dir: java.io.File,
               moreIvlFlags: Seq[String] = Seq.empty[String],
               moreIvlCFlags: Seq[String] = Seq.empty[String]): (Seq[String], Seq[String]) = {

    val ivlFlags = Seq(
      "-m ./%s/%s.vpi".format(dir.toString, topModule),
      "-g2005-sv",
      "-DCLOCK_PERIOD=1"
    ) ++ moreIvlFlags

    val ivlCFlags = Seq(
      s"-o $topModule.vpi",
      "-D__ICARUS__",
      "-I$IVL_HOME",
      s"-I$dir",
      "-fPIC",
      "-std=c++11",
      "-lvpi",
      "-lveriuser",
      "-shared"
    ) ++ moreIvlCFlags

    (ivlFlags, ivlCFlags)
  }

  def constructCSimulatorCommand(
                                    topModule: String,
                                    dir: java.io.File,
                                    harness:  java.io.File,
                                    iFlags: Seq[String] = Seq.empty[String],
                                    iCFlags: Seq[String] = Seq.empty[String]
                                ): String = {

    val (cFlags, cCFlags) = composeFlags(topModule, dir,
      iFlags ++ blackBoxVerilogList(dir) ++ Seq("-o", topModule, s"$topModule.v", harness.toString),
      iCFlags
    )

    composeCommand(topModule, dir, cFlags, cCFlags)
  }

  def apply(
    topModule: String,
    dir: java.io.File,
    ivlHarness: java.io.File,
    moreIvlFlags: Seq[String] = Seq.empty[String],
    moreIvlCFlags: Seq[String] = Seq.empty[String],
    editCommands: String = ""): ProcessBuilder = {

    val finalCommand = editCSimulatorCommand(constructCSimulatorCommand(topModule, dir, ivlHarness, moreIvlFlags, moreIvlCFlags), editCommands)
    println(s"$finalCommand")

    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] object verilogToVSIM extends EditableBuildCSimulatorCommand {
  val prefix = "vsim-command-edit"
  def composeCommand(
                      topModule: String,
                      dir: java.io.File,
                      vlogFlags: Seq[String],
                      cFlags: Seq[String]
                    ): String = {
    Seq("cd", dir.toString, "&&") ++
      Seq("g++") ++ cFlags ++ Seq("vpi.cpp", "vpi_register.cpp", "&&") ++
      Seq("vlib", "work", "&&") ++
      Seq("vlog") ++ vlogFlags ++ Seq("&&") ++
      // VSIM is unfortunately unable to run properly anywhere else than the folder where vlib folder was created
      // To work around this issue we use a crafted bash file as wrapper for local vsim launch
      Seq("cat << EOF", ">", s"$topModule", "&&\n") ++
      // The following bash logic is intended to separate command line arguments between vsim command line arguments and vsim tcl command
      // VSIM command line arguments will most notably contain execution mode and vopt flags
      // VSIM tcl commands can be very useful to pass setup commands such as "add waves -r /*" for gui usage
      Seq("cd \"\\$( dirname \"\\${BASH_SOURCE[0]}\" )\"\n",
          "sw=0\n",
          "echo \"Additional do commands:\"\n",
          "for arg in \"\\$@\"; do\n",
          "    if [[ \"\\$arg\" == \"--\" && \\$sw == 0 ]]; then\n",
          "        sw=1\n",
          "    elif [[ \\$sw == 0 ]]; then\n",
          "        vsimArgs+=(\"\\$arg\")\n",
          "    else\n",
          "        printf \"%b \" \"\\$arg\" | tee -a runme.fdo\n", // using %b to take \n into account
          "    fi\n",
          "done\n",
          "echo \"\"\n", // clear printf buffering 
          "echo \"\nrun -all\" >> runme.fdo\n",
          "vsim", "-64", "\"\\${vsimArgs[@]}\"", s"-pli ${topModule}.so", s"test", "-do", "runme.fdo", 
          "\nEOF\n") ++
      Seq("chmod", "u+x", s"$topModule") mkString " "
  }

  def composeFlags(
               topModule: String,
               dir: java.io.File,
               moreVlogFlags: Seq[String] = Seq.empty[String],
               moreVsimCFlags: Seq[String] = Seq.empty[String]): (Seq[String], Seq[String]) = {

    val vlogFlags = Seq(
      "+define+CLOCK_PERIOD=1"
    ) ++ moreVlogFlags
    
    
    val cFlags = Seq(
      s"-o $topModule.so",
      "-I$QUESTA_INSTALL_DIR/include",
      "-D__VSIM__",
      s"-I$dir",
      "-fPIC",
      "-std=c++11",
      "-lc",
      "-m64",
      "-lvpi",
      "-lveriuser",
      "-Bsymbolic",
      "-shared"
    ) ++ moreVsimCFlags

    (vlogFlags, cFlags)
  }

  def constructCSimulatorCommand(
    topModule: String,
    dir: java.io.File,
    harness:  java.io.File,
    vlogFlags: Seq[String] = Seq.empty[String],
    vsimCFlags: Seq[String] = Seq.empty[String]
  ): String = {
    val (cFlags, cCFlags) = composeFlags(topModule, dir,
      vlogFlags ++ blackBoxVerilogList(dir) ++ Seq(s"$topModule.v", harness.toString),
      vsimCFlags
    )
    composeCommand(topModule, dir, cFlags, cCFlags)
  }

  def apply(
    topModule: String,
    dir: java.io.File,
    vsimHarness: java.io.File,
    moreVlogFlags: Seq[String] = Seq.empty[String],
    moreVsimCFlags: Seq[String] = Seq.empty[String],
    editCommands: String = ""
  ): ProcessBuilder = {
    val finalCommand = editCSimulatorCommand(constructCSimulatorCommand(topModule, dir, vsimHarness, moreVlogFlags, moreVsimCFlags), editCommands)
    println(s"$finalCommand")
    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] object verilogToVCS extends EditableBuildCSimulatorCommand {
  val prefix = "vcs-command-edit"
  override def composeCommand(
                                 topModule: String,
                                 dir: java.io.File,
                                 flags: Seq[String],
                                 cFlags: Seq[String]): String = {
    Seq("cd", dir.toString, "&&", "vcs") ++ flags mkString " "

  }


  def composeFlags(
                      topModule: String,
                      dir: java.io.File,
                      moreVcsFlags: Seq[String] = Seq.empty[String],
                      moreVcsCFlags: Seq[String] = Seq.empty[String]): (Seq[String], Seq[String]) = {

    val ccFlags = Seq("-I$VCS_HOME/include", "-I$dir", "-fPIC", "-std=c++11") ++ moreVcsCFlags

    val vcsFlags = Seq("-full64",
      "-quiet",
      "-timescale=1ns/1ps",
      "-debug_access+all",
      s"-Mdir=$topModule.csrc",
      "+v2k", "+vpi",
      "+vcs+lic+wait",
      "+vcs+initreg+random",
      "+define+CLOCK_PERIOD=1",
      "-P", "vpi.tab",
      "-cpp", "g++", "-O2", "-LDFLAGS", "-lstdc++",
      "-CFLAGS", "\"%s\"".format(ccFlags mkString " ")) ++
      moreVcsFlags

    (vcsFlags, ccFlags)
  }

  def constructCSimulatorCommand(
                                    topModule: String,
                                    dir: java.io.File,
                                    harness:  java.io.File,
                                    iFlags: Seq[String] = Seq.empty[String],
                                    iCFlags: Seq[String] = Seq.empty[String]
                                ): String = {

    val (cFlags, cCFlags) = composeFlags(topModule, dir,
      iFlags ++ blackBoxVerilogList(dir) ++ Seq("-o", topModule, s"$topModule.v", harness.toString, "vpi.cpp"),
      iCFlags
    )

    composeCommand(topModule, dir, cFlags, cCFlags)
  }

  def apply(
    topModule: String,
    dir: java.io.File,
    vcsHarness: java.io.File,
    moreVcsFlags: Seq[String] = Seq.empty[String],
    moreVcsCFlags: Seq[String] = Seq.empty[String],
    editCommands: String = ""): ProcessBuilder = {

    val finalCommand = editCSimulatorCommand(constructCSimulatorCommand(topModule, dir, vcsHarness, moreVcsFlags, moreVcsCFlags), editCommands)
    println(s"$finalCommand")

    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] object verilogToVerilator extends EditableBuildCSimulatorCommand {
  val prefix = "verilator-command-edit"
  override def composeCommand(
                                 topModule: String,
                                 dir: java.io.File,
                                 flags: Seq[String],
                                 cFlags: Seq[String]): String = {
    Seq("cd", dir.getAbsolutePath, "&&", "verilator", "--cc", s"$topModule.v") ++ flags mkString " "

  }

  def composeFlags(
               topModule: String,
               dir: File,
               moreVerilatorFlags: Seq[String] = Seq.empty[String],
               moreVerilatorCFlags: Seq[String] = Seq.empty[String]): (Seq[String], Seq[String]) = {

    val ccFlags = Seq(
      "-Wno-undefined-bool-conversion",
      "-O1",
      s"-DTOP_TYPE=V$topModule",
      "-DVL_USER_FINISH",
      s"-include V$topModule.h"
    ) ++ moreVerilatorCFlags

    val verilatorFlags = Seq("--assert",
      "-Wno-fatal",
      "-Wno-WIDTH",
      "-Wno-STMTDLY",
      "-O1",
      "--top-module", topModule,
      "+define+TOP_TYPE=V" + topModule,
      s"+define+PRINTF_COND=!$topModule.reset",
      s"+define+STOP_COND=!$topModule.reset",
      "-CFLAGS", "\"%s\"".format(ccFlags mkString " "),
      "-Mdir", dir.getAbsolutePath
    ) ++ moreVerilatorFlags
    (verilatorFlags, ccFlags)
  }

  def constructCSimulatorCommand(
                                    topModule: String,
                                    dir: java.io.File,
                                    harness:  java.io.File,
                                    iFlags: Seq[String] = Seq.empty[String],
                                    iCFlags: Seq[String] = Seq.empty[String]
                                ): String = {

    val (cFlags, cCFlags) = composeFlags(topModule, dir,
      blackBoxVerilogList(dir) ++ Seq("--exe", harness.getAbsolutePath) ++ iFlags,
      iCFlags
    )

    composeCommand(topModule, dir, cFlags, cCFlags)
  }

  def apply(
               topModule: String,
               dir: File,
               verilatorHarness: File,
               moreVerilatorFlags: Seq[String] = Seq.empty[String],
               moreVerilatorCFlags: Seq[String] = Seq.empty[String],
               editCommands: String = ""): ProcessBuilder = {

    val finalCommand = editCSimulatorCommand(constructCSimulatorCommand(topModule, dir, verilatorHarness, moreVerilatorFlags, moreVerilatorCFlags), editCommands)
    println(s"$finalCommand")

    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] case class BackendException(b: String)
  extends Exception(s"Unknown backend: $b. Backend should be firrtl, verilator, ivl, vsim, vcs, or glsim")

private[iotesters] case class TestApplicationException(exitVal: Int, lastMessage: String)
  extends RuntimeException(lastMessage)

private[iotesters] object TesterProcess {
  def apply(cmd: Seq[String], logs: ArrayBuffer[String]): Process = {
    require(new java.io.File(cmd.head).exists, s"${cmd.head} doesn't exist")
    val processBuilder = Process(cmd mkString " ")
    val processLogger = ProcessLogger(println, logs += _) // don't log stdout
    processBuilder run processLogger
  }
  def kill(sim: SimApiInterface) {
    while(!sim.exitValue.isCompleted) sim.process.destroy
    println("Exit Code: %d".format(sim.process.exitValue))
  }
  def kill(p: IVLBackend) {
    kill(p.simApiInterface)
  }
  def kill(p: VCSBackend) {
    kill(p.simApiInterface)
  }
  def kill(p: VerilatorBackend) {
    kill(p.simApiInterface)
  }
  def kill(p: VSIMBackend) {
    kill(p.simApiInterface)
  }
  def kill(p: FirrtlTerpBackend) {
  }
}
