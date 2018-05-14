// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

import chisel3._
import chisel3.core.ActualDirection
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
  def apply(dut: MultiIOModule, separator: String = "."): Seq[(Element, String)] =
    dut.getPorts.flatMap { case chisel3.internal.firrtl.Port(data, _) =>
      apply(data.pathName replace (".", separator), data)
    }

}

private[iotesters] object getPorts {
  def apply(dut: MultiIOModule, separator: String = "."): (Seq[(Element, String)], Seq[(Element, String)]) =
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
          case x: DefRegInit => flatten(x.id)
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

private[iotesters] object verilogToIVL {
  def constructIvlFlags(
      topModule: String,
      dir: java.io.File,
      moreIvlFlags: Seq[String] = Seq.empty[String]): Seq[String] = {

    val blackBoxVerilogList = {
      val list_file = new File(dir, firrtl.transforms.BlackBoxSourceHelper.fileListName)
      if(list_file.exists()) {
        Seq("-f", list_file.getAbsolutePath)
      }
      else {
        Seq.empty[String]
      }
    }

    val ivlFlags = Seq(
      "-m ./%s/%s.vpi".format(dir.toString, topModule),
      "-g2005-sv",
      "-DCLOCK_PERIOD=1") ++
      moreIvlFlags ++
      blackBoxVerilogList

    ivlFlags
  }

  def constructIvlCFlags(
      topModule: String,
      dir: java.io.File,
      moreIvlCFlags: Seq[String] = Seq.empty[String]): Seq[String] = {

    val DefaultCcFlags = Seq("-I$IVL_HOME", s"-I$dir", "-fPIC", "-std=c++11", "-lvpi", "-lveriuser", "-shared")

    val ivlCFlags = Seq(
      s"-o $topModule.vpi", "-D__ICARUS__") ++ 
      DefaultCcFlags ++ moreIvlCFlags

    ivlCFlags
  }

  def apply(
    topModule: String,
    dir: java.io.File,
    ivlHarness: java.io.File,
    moreIvlFlags: Seq[String] = Seq.empty[String],
    moreIvlCFlags: Seq[String] = Seq.empty[String],
    editCommands: String = ""): ProcessBuilder = {

    val ivlFlags = constructIvlFlags(topModule, dir, moreIvlFlags)
    val ivlCFlags = constructIvlCFlags(topModule, dir, moreIvlCFlags)

    val cmd = Seq("cd", dir.toString, "&&") ++
                Seq("g++") ++ ivlCFlags ++ Seq("vpi.cpp", "vpi_register.cpp", "&&") ++
                Seq("iverilog") ++ ivlFlags ++ Seq("-o", topModule, s"$topModule.v", ivlHarness.toString) mkString " "

    val commandEditor = CommandEditor(editCommands, "ivl-command-edit")
    val finalCommand = commandEditor(cmd)
    println(s"$finalCommand")

    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] object verilogToVCS {
  def constructVcsFlags(
      topModule: String,
      dir: java.io.File,
      moreVcsFlags: Seq[String] = Seq.empty[String],
      moreVcsCFlags: Seq[String] = Seq.empty[String]): Seq[String] = {

    val DefaultCcFlags = Seq("-I$VCS_HOME/include", "-I$dir", "-fPIC", "-std=c++11")
    val ccFlags = DefaultCcFlags ++ moreVcsCFlags

    val blackBoxVerilogList = {
      val list_file = new File(dir, firrtl.transforms.BlackBoxSourceHelper.fileListName)
      if(list_file.exists()) {
        Seq("-f", list_file.getAbsolutePath)
      }
      else {
        Seq.empty[String]
      }
    }

    val vcsFlags = Seq("-full64",
      "-quiet",
      "-timescale=1ns/1ps",
      "-debug_pp",
      s"-Mdir=$topModule.csrc",
      "+v2k", "+vpi",
      "+vcs+lic+wait",
      "+vcs+initreg+random",
      "+define+CLOCK_PERIOD=1",
      "-P", "vpi.tab",
      "-cpp", "g++", "-O2", "-LDFLAGS", "-lstdc++",
      "-CFLAGS", "\"%s\"".format(ccFlags mkString " ")) ++
      moreVcsFlags ++
      blackBoxVerilogList

    vcsFlags
  }

  def apply(
    topModule: String,
    dir: java.io.File,
    vcsHarness: java.io.File,
    moreVcsFlags: Seq[String] = Seq.empty[String],
    moreVcsCFlags: Seq[String] = Seq.empty[String],
    editCommands: String = ""): ProcessBuilder = {

    val vcsFlags = constructVcsFlags(topModule, dir, moreVcsFlags, moreVcsCFlags)

    val cmd = Seq("cd", dir.toString, "&&", "vcs") ++ vcsFlags ++ Seq(
      "-o", topModule, s"$topModule.v", vcsHarness.toString, "vpi.cpp") mkString " "

    val commandEditor = CommandEditor(editCommands, "vcs-command-edit")
    val finalCommand = commandEditor(cmd)
    println(s"$finalCommand")

    Seq("bash", "-c", finalCommand)
  }
}

private[iotesters] case class BackendException(b: String)
  extends Exception(s"Unknown backend: $b. Backend should be firrtl, verilator, ivl, vcs, or glsim")

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
  def kill(p: FirrtlTerpBackend) {
  }
}
