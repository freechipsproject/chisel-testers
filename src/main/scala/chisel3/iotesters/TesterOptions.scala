// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import java.io.File

import chisel3.HasChiselExecutionOptions
import firrtl.{ComposableOptions, ExecutionOptionsManager, HasFirrtlOptions}

import scala.util.matching.Regex

case class TesterOptions(
  isGenVerilog:         Boolean = false,
  isGenHarness:         Boolean = false,
  isCompiling:          Boolean = false,
  isRunTestc:            Boolean = false,
  isVerbose:            Boolean = false,
  displayBase:          Int     = 10,
  testerSeed:           Long    = System.currentTimeMillis,
  testCmd:              Seq[String] = Seq.empty,
  moreVcsFlags:         Seq[String] = Seq.empty,
  moreVcsCFlags:        Seq[String] = Seq.empty,
  vcsCommandEdits:      String = "",
  backendName:          String  = "treadle",
  logFileName:          String  = "",
  waveform:             Option[File] = None,
  moreIvlFlags:         Seq[String] = Seq.empty,
  moreIvlCFlags:        Seq[String] = Seq.empty,
  ivlCommandEdits:      String = "",
  moreVlogFlags:        Seq[String] = Seq.empty,
  moreVsimCFlags:       Seq[String] = Seq.empty,
  moreVsimFlags:        Seq[String] = Seq.empty,
  moreVsimDoCmds:       Seq[String] = Seq.empty,
  vsimCommandEdits:     String = "",
  generateVcdOutput:    String = "",
  generateFsdbOutput:    String = ""
) extends ComposableOptions

object TesterOptions {
  val VcsFileCommands: Regex = """file:(.+)""".r
  val IvlFileCommands: Regex = """file:(.+)""".r
}

trait HasTesterOptions {
  self: ExecutionOptionsManager =>

  var testerOptions = TesterOptions()

  parser.note("tester options")

  parser.opt[String]("backend-name").valueName("<firrtl|treadle|verilator|ivl|vcs>")
    .abbr("tbn")
    .validate { x =>
      if (Array("firrtl", "treadle", "verilator", "ivl", "vcs", "vsim").contains(x.toLowerCase)) parser.success
      else parser.failure(s"$x not a legal backend name")
    }
    .foreach { x => testerOptions = testerOptions.copy(backendName = x) }
    .text(s"backend to use with tester, default is ${testerOptions.backendName}")

  parser.opt[Unit]("is-gen-verilog")
    .abbr("tigv")
    .foreach { _ => testerOptions = testerOptions.copy(isGenVerilog = true) }
    .text("has verilog already been generated")

  parser.opt[Unit]("is-gen-harness")
    .abbr("tigh")
    .foreach { _ => testerOptions = testerOptions.copy(isGenHarness = true) }
    .text("has harness already been generated")

  parser.opt[Unit]("is-compiling")
    .abbr("tic")
    .foreach { _ => testerOptions = testerOptions.copy(isCompiling = true) }
    .text("has harness already been generated")

  parser.opt[Unit]("is-verbose")
    .abbr("tiv")
    .foreach { _ => testerOptions = testerOptions.copy(isVerbose = true) }
    .text(s"set verbose flag on PeekPokeTesters, default is ${testerOptions.isVerbose}")

  parser.opt[Int]("display-base")
    .abbr("tdb")
    .foreach { x => testerOptions = testerOptions.copy(displayBase = x) }
    .text(s"provides a seed for random number generator, default is ${testerOptions.displayBase}")

  parser.opt[String]("test-command")
    .abbr("ttc")
    .foreach { x => testerOptions = testerOptions.copy(testCmd = x.split("""\s""")) }
    .text("Change the command run as the backend. Quote this if it contains spaces")

  parser.opt[String]("more-vcs-flags")
    .abbr("tmvf")
    .foreach { x => testerOptions = testerOptions.copy(moreVcsFlags = x.split("""\s""")) }
    .text("Add specified commands to the VCS/Verilator command line")

  parser.opt[String]("more-vcs-c-flags")
    .abbr("tmvf")
    .foreach { x => testerOptions = testerOptions.copy(moreVcsCFlags = x.split("""\s""")) }
    .text("Add specified commands to the CFLAGS on the VCS/Verilator command line")

  parser.opt[String]("vcs-command-edits")
    .abbr("tvce")
    .foreach { x =>
      testerOptions = testerOptions.copy(vcsCommandEdits = x) }
    .text("a file containing regex substitutions, one per line s/pattern/replacement/")

  parser.opt[String]("more-ivl-flags")
    .abbr("tmif")
    .foreach { x => testerOptions = testerOptions.copy(moreIvlFlags = x.split("""\s""")) }
    .text("Add specified commands to the ivl command line")

  parser.opt[String]("more-ivl-c-flags")
    .abbr("tmicf")
    .foreach { x => testerOptions = testerOptions.copy(moreIvlCFlags = x.split("""\s""")) }
    .text("Add specified commands to the CFLAGS on the ivl command line")

  parser.opt[String]("ivl-command-edits")
    .abbr("tice")
    .foreach { x =>
      testerOptions = testerOptions.copy(ivlCommandEdits = x) }
    .text("a file containing regex substitutions, one per line s/pattern/replacement/")

  parser.opt[String]("more-vlog-flags")
    .abbr("tmvlf")
    .foreach { x => testerOptions = testerOptions.copy(moreVlogFlags = x.split("""\s""")) }
    .text("Add specified commands to the vlog command line")
    
  parser.opt[String]("more-vsim-flags")
    .abbr("tmvsf")
    .foreach { x => testerOptions = testerOptions.copy(moreVsimFlags = x.split("""\s""")) }
    .text("Add specified commands to the vsim command line startup")
    
  parser.opt[String]("more-vsim-do-cmds")
    .abbr("tmvsd")
    .foreach { x => testerOptions = testerOptions.copy(moreVsimDoCmds = testerOptions.moreVsimDoCmds ++ Seq(x)) }
    .text("Execute specified commands within vsim interpreter before simulation run")

  parser.opt[String]("more-vsim-c-flags")
    .abbr("tmvscf")
    .foreach { x => testerOptions = testerOptions.copy(moreVsimCFlags = x.split("""\s""")) }
    .text("Add specified commands to the g++ vpi compilation command line for use with vsim backend")

  parser.opt[String]("vsim-command-edits")
    .abbr("tvsce")
    .foreach { x =>
      testerOptions = testerOptions.copy(vsimCommandEdits = x) }
    .text("a file containing regex substitutions, one per line s/pattern/replacement/")

  parser.opt[String]("log-file-name")
    .abbr("tlfn")
    .foreach { x => testerOptions = testerOptions.copy(logFileName = x) }
    .text("write log file")

  parser.opt[File]("wave-form-file-name")
    .abbr("twffn")
    .foreach { x => testerOptions = testerOptions.copy(waveform = Some(x)) }
    .text("wave form file name")

  parser.opt[Long]("test-seed")
    .abbr("tts")
    .foreach { x => testerOptions = testerOptions.copy(testerSeed = x) }
    .text("provides a seed for random number generator")

  parser.opt[String]("generate-vcd-output")
    .abbr("tgvo")
    .validate { x =>
      if(Seq("on", "off").contains(x.toLowerCase)) {
        parser.success
      } else {
        parser.failure("generateVcdOutput must be set to on or off")
      }
    }
    .foreach { x => testerOptions = testerOptions.copy(generateVcdOutput = x) }
    .text(s"""set this flag to "on" or "off", otherwise it defaults to on for vcs, off for scala backends""")

  parser.opt[String]("generate-fsdb-output")
    .abbr("tgfo")
    .validate { x =>
      if(Seq("on", "off").contains(x.toLowerCase)) {
        parser.success
      } else {
        parser.failure("generateFsdbOutput must be set to on or off")
      }
    }
    .foreach { x => testerOptions = testerOptions.copy(generateFsdbOutput = x) }
    .text(s"""set this flag to "on" or "off", otherwise it defaults to off""")
}

class TesterOptionsManager
  extends ExecutionOptionsManager("chisel-testers")
    with HasTesterOptions
    with HasChiselExecutionOptions
    with HasFirrtlOptions
    with HasTreadleSuite {
}

