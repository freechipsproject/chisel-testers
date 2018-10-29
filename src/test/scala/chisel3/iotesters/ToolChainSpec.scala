// See LICENSE for license details.

package chisel3.iotesters

import java.io.{File, PrintWriter}

import org.scalatest.{FreeSpec, Matchers}

class ToolChainSpec extends FreeSpec with Matchers {
  case class EditableBuildCommandTest(
      name: String,
      builder: EditableBuildCSimulatorCommand,
      gFlagName: String,
      gFlagFieldName: String,
      cFlagName: String,
      cFlagFieldName: String,
      editName: String,
      editFieldName: String
                                     )
  val editableBuildCommandTests = List(
    EditableBuildCommandTest("verilogToVCS", verilogToVCS, "--more-vcs-flags", "moreVcsFlags", "--more-vcs-c-flags", "moreVcsCFlags", "--vcs-command-edits", "vcsCommandEdits"),
    EditableBuildCommandTest("verilogToIVL", verilogToIVL, "--more-ivl-flags", "moreIvlFlags", "--more-ivl-c-flags", "moreIvlCFlags", "--ivl-command-edits", "ivlCommandEdits"),
    EditableBuildCommandTest("verilogToVerilator", verilogToVerilator, "--more-vcs-flags", "moreVcsFlags", "--more-vcs-c-flags", "moreVcsCFlags", "--vcs-command-edits", "vcsCommandEdits"),
  )

  val dummyTop = "top"
  val dummyHarness = new File("harness.v")
  val dummyDir = new File("dir")

  for ( ebct <- editableBuildCommandTests) {
    val builderName = ebct.name
    val builder = ebct.builder

    s"Ability to augment the VCS command lines - $builderName" - {

      "Can add arguments to VCS flags" in {
        val flag1 = "--dog"
        val flag2 = "--cat"

        val manager = new TesterOptionsManager

        manager.parse(Array(ebct.gFlagName, s"$flag1 $flag2")) should be (true)
        // TODO It would be nice if we could do this dynamically (based on reflection).
        val gFlags: Seq[String] = ebct.gFlagFieldName match {
          case "moreVcsFlags" => manager.testerOptions.moreVcsFlags
          case "moreIvlFlags" => manager.testerOptions.moreIvlFlags
        }
        val cFlags: Seq[String] = ebct.cFlagFieldName match {
          case "moreVcsCFlags" => manager.testerOptions.moreVcsCFlags
          case "moreIvlCFlags" => manager.testerOptions.moreIvlCFlags
        }

        gFlags.length should be (2)

        val (vcsFlags, vcsCFlags) = builder.composeFlags(dummyTop, dummyDir, gFlags, cFlags)

        vcsFlags.contains(flag1) should be (true)
        vcsFlags.contains(flag2) should be (true)
      }

      s"Can add arguments to VCS CFLAGS - $builderName" in {
        val cFlag1 = "-XYZ"
        val cFlag2 = "-DFLAG=VALUE"
        val manager = new TesterOptionsManager

        manager.parse(Array(ebct.cFlagName, s"$cFlag1 $cFlag2")) should be (true)

        // TODO It would be nice if we could do this dynamically (based on reflection).
        val gFlags: Seq[String] = ebct.gFlagFieldName match {
          case "moreVcsFlags" => manager.testerOptions.moreVcsFlags
          case "moreIvlFlags" => manager.testerOptions.moreIvlFlags
        }
        val cFlags: Seq[String] = ebct.cFlagFieldName match {
          case "moreVcsCFlags" => manager.testerOptions.moreVcsCFlags
          case "moreIvlCFlags" => manager.testerOptions.moreIvlCFlags
        }
        cFlags.length should be (2)

        val (vcsFlags, vcsCFlags) = builder.composeFlags(
          dummyTop, dummyDir, gFlags, cFlags)

        vcsCFlags.contains(cFlag1) should be (true)
        vcsCFlags.contains(cFlag2) should be (true)
      }
    }

    s"Ability to edit vcs command line - $builderName" - {
      // Build the expected command (default arguments)
      val expectedCommand = builder.constructCSimulatorCommand(dummyTop, dummyDir, dummyHarness)
      val expectedMultipleEditVCSCommand = """cd dir && vcs -full64 -loud -timescale=1ns/1ps -debug_pp -Mdir=top.csrc +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 -P vpi.tab -cpp g++ -O2 -LDFLAGS -lstdc++ -CFLAGS "-I$VCS_HOME/include -I$dir -fPIC -std=c++11" -o top top.v harness.v vpi.cpp"""

      "can be done from a single edit on command line" in {
        val dummyArg = "-A-dummy-arg"
        val manager = new TesterOptionsManager

        manager.parse(Array(ebct.editName, s"s/ $dummyArg//")) should be(true)

        val editCommands = ebct.editFieldName match {
          case "vcsCommandEdits" => manager.testerOptions.vcsCommandEdits
          case "ivlCommandEdits" => manager.testerOptions.ivlCommandEdits
        }

        // Build the new command with the additional dummy argument(without editing)
        val newCommand = builder.constructCSimulatorCommand(dummyTop, dummyDir, dummyHarness, Seq(dummyArg))
        // Verify this new command doesn't match the "expected" (edited) command
        newCommand should not be(expectedCommand)
        newCommand should include (dummyArg)

        // Now build and edit the command and verify it matches the expected command.
        val editedCommand = builder.editCSimulatorCommand(builder.constructCSimulatorCommand(dummyTop, dummyDir, dummyHarness, Seq(dummyArg)), editCommands)

        editedCommand should be(expectedCommand)
      }

      "can be done from a multiple edits in a file" in {
        val edits = Seq(
          s"""verbose""",
          s"""s/-quiet /-loud /""",
          s"""s/\\+v2k \\+vpi //""",
          s"""s/-lveriuser//"""
        )
        val fileName = s"edit-file.$builderName"
        val file = new java.io.File(fileName)
        val writer = new PrintWriter(file)
        for (edit <- edits)
          writer.println(edit)
        writer.close()

        val manager = new TesterOptionsManager

        manager.parse(Array(ebct.editName, s"file:$fileName")) should be(true)

        val editCommands = ebct.editFieldName match {
          case "vcsCommandEdits" => manager.testerOptions.vcsCommandEdits
          case "ivlCommandEdits" => manager.testerOptions.ivlCommandEdits
        }
        // Use the edit commands (as parsed) to edit the expected command
        val expectedEditedCommand = CommandEditor(editCommands, "prep-exp")(expectedCommand)

        // Now build and edit command and verify it matches the expected command.
        val editedCommand = builder.editCSimulatorCommand(builder.constructCSimulatorCommand(dummyTop, dummyDir, dummyHarness), editCommands)
        file.delete()

        editedCommand should be(expectedEditedCommand)
        // NOTE: Since we're using the CommandEditor in both cases (directly for verification and indirectly for test),
        //  it could be broken and we wouldn't notice it. For at least one of the command builders, we've memorized the
        //  expected edit results and we check against those to verify that CommandEditor is behaving as expected.
        if (builder == verilogToVCS)
          editedCommand.trim should be(expectedMultipleEditVCSCommand.trim)

      }
    }
  }

}
