// See LICENSE for license details.

package chisel3.iotesters

import java.io.{File, PrintWriter}

import org.scalatest.{FreeSpec, Matchers}

class ToolChainSpec extends FreeSpec with Matchers {
  "Ability to augment the VCS command lines" - {
    "Can add arguments to VCS flags" in {
      val flag1 = "--dog"
      val flag2 = "--cat"

      val manager = new TesterOptionsManager

      manager.parse(Array("--more-vcs-flags", s"$flag1 $flag2")) should be (true)

      manager.testerOptions.moreVcsFlags.length should be (2)

      val vcsFlags = verilogToVCS.constructVcsFlags(
        "top", new File("dir"), manager.testerOptions.moreVcsFlags, manager.testerOptions.moreVcsCFlags)

      vcsFlags.contains(flag1) should be (true)
      vcsFlags.contains(flag2) should be (true)
    }
    "Can add arguments to VCS CFLAGS" in {
      val cFlag1 = "-XYZ"
      val cFlag2 = "-DFLAG=VALUE"
      val manager = new TesterOptionsManager

      manager.parse(Array("--more-vcs-c-flags", s"$cFlag1 $cFlag2")) should be (true)

      manager.testerOptions.moreVcsCFlags.length should be (2)

      val vcsFlags = verilogToVCS.constructVcsFlags(
        "top", new File("dir"), manager.testerOptions.moreVcsFlags, manager.testerOptions.moreVcsCFlags)

      vcsFlags.exists(flag => flag.contains(cFlag1)) should be (true)
      vcsFlags.exists(flag => flag.contains(cFlag2)) should be (true)
    }
  }

  "Ability to edit vcs command line" - {
    "can be done from a single edit on command line" in {
      val command = """cd mydir && vcs -full64 -quiet -timescale=1ns/1ps -debug_pp -Mdir=bitwise_neg.csrc +v2k +vpi +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 -P vpi.tab -cpp g++ -O2 -LDFLAGS -lstdc++ -CFLAGS "-I$VCS_HOME/include -I$dir -fPIC -std=c++11" -o bitwise_neg bitwise_neg.v bitwise_neg-harness.v vpi.cpp"""
      val expectedCommand = """cd mydir && vcs -quiet -timescale=1ns/1ps -debug_pp -Mdir=bitwise_neg.csrc +v2k +vpi +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 -P vpi.tab -cpp g++ -O2 -LDFLAGS -lstdc++ -CFLAGS "-I$VCS_HOME/include -I$dir -fPIC -std=c++11" -o bitwise_neg bitwise_neg.v bitwise_neg-harness.v vpi.cpp"""
      val manager = new TesterOptionsManager

      manager.parse(Array("--vcs-command-edits", "s/-full64 //")) should be(true)

      val editor = CommandEditor(manager.testerOptions.vcsCommandEdits, "command-edit-test")
      val newCommand = editor(command)

      newCommand should be(expectedCommand)
    }

    "can be done from a multiple edits in a file" in {
      val file = new java.io.File("edit-file")
      val writer = new PrintWriter(file)
      writer.println(s"""verbose""")
      writer.println(s"""s/-quiet /-loud /""")
      writer.println(s"""s/\\+v2k \\+vpi //""") // escape + because it's magic to regex
      writer.close()

      val command = """cd mydir && vcs -full64 -quiet -timescale=1ns/1ps -debug_pp -Mdir=bitwise_neg.csrc +v2k +vpi +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 -P vpi.tab -cpp g++ -O2 -LDFLAGS -lstdc++ -CFLAGS "-I$VCS_HOME/include -I$dir -fPIC -std=c++11" -o bitwise_neg bitwise_neg.v bitwise_neg-harness.v vpi.cpp"""
      val expectedCommand = """cd mydir && vcs -full64 -loud -timescale=1ns/1ps -debug_pp -Mdir=bitwise_neg.csrc +vcs+lic+wait +vcs+initreg+random +define+CLOCK_PERIOD=1 -P vpi.tab -cpp g++ -O2 -LDFLAGS -lstdc++ -CFLAGS "-I$VCS_HOME/include -I$dir -fPIC -std=c++11" -o bitwise_neg bitwise_neg.v bitwise_neg-harness.v vpi.cpp"""
      val manager = new TesterOptionsManager

      manager.parse(Array("--vcs-command-edits", "file:edit-file")) should be(true)

      val editor = CommandEditor(manager.testerOptions.vcsCommandEdits, "command-edit-test")
      val newCommand = editor(command)

      newCommand should be(expectedCommand)

      file.delete()
    }
  }

}
