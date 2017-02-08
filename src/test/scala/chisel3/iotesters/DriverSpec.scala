// See LICENSE for license details.

package chisel3.iotesters

import java.io.File

import org.scalatest.{FreeSpec, Matchers}
import chisel3._

class DriverTest extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(1.W))
    val out = Output(UInt(1.W))
  })
  io.out := io.in
}

class DriverTestTester(c: DriverTest) extends PeekPokeTester(c) {
  poke(c.io.in, 1)
  expect(c.io.out, 1)
}

class DriverSpec extends FreeSpec with Matchers {
  /**
    * recursively delete all directories in a relative path
    * DO NOT DELETE absolute paths
    *
    * @param file: a directory hierarchy to delete
    */
  def deleteDirectoryHierarchy(file: File): Unit = {
    if(file.getAbsolutePath.split("/").last.isEmpty || file.getAbsolutePath == "/") {
      // don't delete absolute path
    }
    else {
      if(file.isDirectory) {
        file.listFiles().foreach( f => deleteDirectoryHierarchy(f) )
      }
      file.delete()
    }
  }

  "tester driver should support a wide range of downstream toolchain options" - {

    "default options should not fail" in {
      chisel3.iotesters.Driver.execute(
        Array.empty[String],
        () => new DriverTest) { c =>
        new DriverTestTester(c) } should be (true)
    }

    "bad arguments should fail" in {
      chisel3.iotesters.Driver.execute(
        Array("--i-am-a-bad-argument"),
        () => new DriverTest) { c => new DriverTestTester(c)} should be (false)
    }

    "this is the way to override the target directory" - {
      "specifying targetDir alone will create a subdir" in {
        val driverTestDir = "driver_spec_test_1"
        chisel3.iotesters.Driver.execute(
          Array("--target-dir", driverTestDir),
          () => new DriverTest
        ) { c =>
          new DriverTestTester(c)
        } should be (true)
        val dir = new File(driverTestDir)
        dir.exists() should be (true)
        //
        dir.listFiles().exists { f =>
          f.getAbsolutePath.split("/").last.startsWith("chisel3.iotesters.DriverSpec") &&
            f.isDirectory
        }  should be (true)
        deleteDirectoryHierarchy(new File(driverTestDir))
      }

      "specifying targetDir and topName will avoid the subdir" in {
        val driverTestDir = "driver_spec_test_2"
        chisel3.iotesters.Driver.execute(
          Array("--target-dir", driverTestDir, "--top-name", "DriverTest"),
          () => new DriverTest
        ) { c =>
          new DriverTestTester(c)
        } should be (true)
        val dir = new File(driverTestDir)
        dir.exists() should be (true)
        //
        dir.listFiles().exists { f =>
          f.getAbsolutePath.split("/").last == "DriverTest.v"
        } should be (true)
        deleteDirectoryHierarchy(new File(driverTestDir))
      }
    }

    "example of setting test command" in {
      val manager = new TesterOptionsManager {
        testerOptions = testerOptions.copy(backendName = "verilator", testCmd = Seq("foo2/VDriverTest"))
        commonOptions = commonOptions.copy(targetDirName = "foo2", topName = "DriverTest")
        interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
      }
      iotesters.Driver.execute(() => new DriverTest, manager) { c =>
        new DriverTestTester(c)
      } should be (true)

      deleteDirectoryHierarchy(new File("foo2"))
    }
  }
}
