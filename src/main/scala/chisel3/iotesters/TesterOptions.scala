// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

import chisel3.experimental.{MultiIOModule, RawModule}
import firrtl.annotations.{DeletedAnnotation, NoTargetAnnotation, Unserializable}
import firrtl.{AnnotationSeq, EmittedCircuit}
import firrtl.options.OptionsView
import firrtl.options.{HasScoptOptions, RegisteredLibrary}
import scopt.OptionParser

import scala.util.matching.Regex

case class TesterOptions(
                          isGenVerilog:    Boolean = false,
                          isGenHarness:    Boolean = false,
                          isCompiling:     Boolean = false,
                          isRunTest:       Boolean = false,
                          isVerbose:       Boolean = false,
                          displayBase:     Int     = 10,
                          testerSeed:      Long    = System.currentTimeMillis,
                          testCmd:         Seq[String] = Seq.empty,
                          moreVcsFlags:    Seq[String] = Seq.empty,
                          moreVcsCFlags:   Seq[String] = Seq.empty,
                          vcsCommandEdits: String = "",
                          backendName:     String  = "treadle",
                          logFileName:     String  = "",
                          waveform:        Option[File] = None,
                          moreIvlFlags:    Seq[String] = Seq.empty,
                          moreIvlCFlags:   Seq[String] = Seq.empty,
                          ivlCommandEdits: String = "")

object TesterOptions {
  val VcsFileCommands: Regex = """file:(.+)""".r
  val IvlFileCommands: Regex = """file:(.+)""".r
}

sealed trait TesterOption extends HasScoptOptions

case class TesterBackendAnnotation(backendName: String = "") extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("backend-name")
    .valueName ("<firrtl|treadle|verilator|ivl|vcs>")
    .abbr ("tbn")
    .validate { x =>
      if (Array ("firrtl", "treadle", "verilator", "ivl", "vcs").contains (x.toLowerCase) ) p.success
      else p.failure (s"$x not a legal backend name")
    }
    .action { (x, c) => c :+ TesterBackendAnnotation(x) }
    .text (s"backend to use with tester, default is ${TesterOptions().backendName}")
}


//sealed trait EmittedAnnotation[T <: PeekPokeTester] extends NoTargetAnnotation {
//  val value: T
//}

//sealed trait PeekPokeTesterAnnotation[T <: EmittedCircuit] extends EmittedAnnotation[T]


//case class TesterPeekPoker[T](peekPokeTester: PeekPokeTester) extends NoTargetAnnotation with TesterOption {
//  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("backend-name")
//    .valueName ("<firrtl|treadle|verilator|ivl|vcs>")
//    .abbr ("tbn")
//    .validate { x =>
//      if (Array ("firrtl", "treadle", "verilator", "ivl", "vcs").contains (x.toLowerCase) ) p.success
//      else p.failure (s"$x not a legal backend name")
//    }
//    .action { (x, c) => c :+ TesterBackendAnnotation(x) }
//    .text (s"backend to use with tester, default is ${TesterOptions().backendName}")
//}

case object TesterIsGenVerilog extends NoTargetAnnotation with TesterOption {
  override def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Unit]("is-gen-verilog")
    .abbr("tigv")
    .action { (_, c) => c :+ this }
    .unbounded()
    .text("has verilog already been generated")
}

case object TesterIsGenHarness extends NoTargetAnnotation with TesterOption {
  override def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Unit]("is-gen-harness")
    .abbr("tigh")
    .action { (_, c) => c :+ this }
    .unbounded()
    .text("has harness already been generated")
}

case object TesterIsCompiling extends NoTargetAnnotation with TesterOption {
  override def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Unit]("is-compiling")
    .abbr("tic")
    .action { (_, c) => c :+ this }
    .unbounded()
    .text("has harness already been generated")
}

case object TesterIsVerbose extends NoTargetAnnotation with TesterOption {
  override def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Unit]("is-verbose")
    .abbr("tiv")
    .action { (_, c) => c :+ this }
    .unbounded()
    .text(s"set verbose flag on PeekPokeTesters, default is ${TesterOptions().isVerbose}")
}

case class TesterDisplayBase(base: Int = 10) extends NoTargetAnnotation with TesterOption {
  override def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Unit]("display-base")
    .abbr("tdb")
    .action { (_, c) => c :+ this }
    .unbounded()
    .text(s"default display radix, default is ${TesterOptions().displayBase}")
}

case class TesterTestCommand(command: Seq[String] = Seq.empty) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("test-command")
    .abbr ("ttc")
    .action { (x, c) => c :+ TesterTestCommand(x.split("""\s""").toSeq) }
    .text("Change the command run as the backend. Quote this if it contains spaces")
}

case class TesterMoreVcsFlags(moreVcsFlags: Seq[String] = Seq.empty) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("more-vcs-flags")
    .abbr ("tmvf")
    .action { (x, c) => c :+ TesterMoreVcsFlags(x.split("""\s""").toSeq) }
    .text("Add specified commands to the VCS command line")
}

case class TesterMoreVcsCFlags(moreVcsCFlags: Seq[String] = Seq.empty) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("more-vcs-c-flags")
    .abbr ("tmvcf")
    .action { (x, c) => c :+ TesterMoreVcsCFlags(x.split("""\s""").toSeq) }
    .text("Add specified commands to the CFLAGS on the VCS command line")
}

case class TesterVcsCommandEdits(command: String = "") extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("vcs-command-edits")
    .abbr ("tvce")
    .action { (x, c) => c :+ TesterVcsCommandEdits(x) }
    .text("a file containing regex substitutions, one per line s/pattern/replacement/")
}

case class TesterMoreIvlFlags(moreVcsFlags: Seq[String] = Seq.empty) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("more-ivl-flags")
    .abbr ("tmif")
    .action { (x, c) => c :+ TesterMoreIvlFlags(x.split("""\s""").toSeq) }
    .text("Add specified commands to the ivl command line")
}

case class TesterMoreIvlCFlags(moreVcsCFlags: Seq[String] = Seq.empty) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("more-ivl-c-flags")
    .abbr ("tmicf")
    .action { (x, c) => c :+ TesterMoreIvlCFlags(x.split("""\s""").toSeq) }
    .text("Add specified commands to the CFLAGS on the ivl command line")
}

case class TesterIvlCommandEdits(command: String = "") extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("ivl-command-edits")
    .abbr ("tice")
    .action { (x, c) => c :+ TesterIvlCommandEdits(x) }
    .text("a file containing regex substitutions, one per line s/pattern/replacement/")
}

case class TesterLogFileName(logFileName: String = "") extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("log-file-name")
    .abbr ("tlfn")
    .action { (x, c) => c :+ TesterLogFileName(x) }
    .text (s"write log file")
}

case class TesterWaveFormFileName(waveFormFileName: String = "") extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[String]("wave-form-file-name")
    .abbr ("twffn")
    .action { (x, c) => c :+ TesterWaveFormFileName(x) }
    .text (s"write log file")
}

case class TesterSeed(seed: Long = 0L) extends NoTargetAnnotation with TesterOption {
  def addOptions(p: OptionParser[AnnotationSeq]): Unit = p.opt[Long]("test-seed")
    .abbr ("tts")
    .action { (x, c) => c :+ TesterSeed(x) }
    .text (s"write log file")
}

/** Holds a function that, when elaborated, produces a [[PeekPokeTester]]
  *
  * @param dut a function that generates a [[PeekPokeTester]]
  * @note This [[firrtl.annotations.Annotation]] is [[firrtl.annotations.Unserializable]].
  *       It will be converted to a [[PeekPokeTester]] if serialized
  *       to JSON.
  */
case class PeekPokeTesterAnnotation[T <: RawModule](tester: (T) => PeekPokeTester[T]) extends NoTargetAnnotation with Unserializable {
  /** Elaborate this dut into a Chisel circuit
    * @return an [[AnnotationSeq]] containing an [[PeekPokeTester]]
    * @note This [[firrtl.annotations.Annotation]] will be preserved as a [[firrtl.annotations.DeletedAnnotation]]
    */
  def elaborate: AnnotationSeq = DeletedAnnotation(this.getClass.getName, this) +: Seq(PeekPokeTesterAnnotation(tester))

  /** Convert this dut to a FIRRTL circuit
    * @return an [[AnnotationSeq]] containing a [[firrtl.FirrtlCircuitAnnotation]]
    */
  //TODO: chick this needs to be fixed
  def convert: AnnotationSeq = Seq.empty

  /** Prepare this [[firrtl.annotations.Annotation]] for JSON serialization
    * @note this simply runs [[convert]]
    * @return an [[AnnotationSeq]] without any [[Unserializable]] annotations
    */
  def toJsonSerializable: AnnotationSeq = convert
}


class TestersLibrary extends RegisteredLibrary {
  val name: String = "testers"
  override def addOptions(parser: OptionParser[AnnotationSeq]): Unit = {
    val seq: Seq[HasScoptOptions] = Seq(
      TesterBackendAnnotation(),
      TesterIsGenVerilog,
      TesterIsGenHarness,
      TesterIsCompiling,
      TesterIsVerbose,
      TesterDisplayBase(),
      TesterTestCommand(),
      TesterMoreVcsFlags(),
      TesterMoreVcsCFlags(),
      TesterVcsCommandEdits(),
      TesterMoreIvlFlags(),
      TesterMoreIvlCFlags(),
      TesterIvlCommandEdits(),
      TesterLogFileName(),
      TesterWaveFormFileName(),
      TesterSeed()
    )

    seq.foreach(_.addOptions(parser))
  }
}


//scalastyle:off cyclomatic.complexity
object TesterOptionsViewer {
  implicit object TreadleOptionsView extends OptionsView[TesterOptions] {
    def view(options: AnnotationSeq): Option[TesterOptions] = {
      val executionOptions = options.foldLeft(TesterOptions()) { (previousOptions, annotation) =>
        annotation match {
          case TesterBackendAnnotation(name)         => previousOptions.copy(backendName = name)
          case TesterIsGenVerilog                    => previousOptions.copy(isGenVerilog = true)
          case TesterIsGenHarness                    => previousOptions.copy(isGenHarness = true)
          case TesterIsCompiling                     => previousOptions.copy(isCompiling = true)
          case TesterIsVerbose                       => previousOptions.copy(isVerbose = true)
          case TesterDisplayBase(radix)              => previousOptions.copy(displayBase = radix.toInt)
          case TesterTestCommand(command)            => previousOptions.copy(testCmd = command)
          case TesterMoreVcsFlags(flags)             => previousOptions.copy(moreVcsFlags = flags)
          case TesterMoreVcsCFlags(flags)            => previousOptions.copy(moreVcsCFlags = flags)
          case TesterVcsCommandEdits(string)         => previousOptions.copy(vcsCommandEdits = string)
          case TesterMoreIvlFlags(flags)             => previousOptions.copy(moreIvlFlags = flags)
          case TesterMoreIvlCFlags(flags)            => previousOptions.copy(moreIvlCFlags = flags)
          case TesterIvlCommandEdits(string)         => previousOptions.copy(ivlCommandEdits = string)
          case TesterLogFileName(name)               => previousOptions.copy(logFileName = name)
          case TesterWaveFormFileName(name)          => previousOptions.copy(waveform = Some(new File(name)))
          case TesterSeed(seed)                      => previousOptions.copy(testerSeed = seed)
          case _ => previousOptions
        }

      }
      Some(executionOptions)
    }
  }
}


