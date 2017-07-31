// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

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

}
