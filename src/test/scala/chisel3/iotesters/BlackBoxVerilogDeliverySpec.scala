// See LICENSE for license details.

package chisel3.iotesters

import chisel3._
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource}
import org.scalatest.{FreeSpec, Matchers}

class BBAddOne extends HasBlackBoxInline {
  val io = IO(new Bundle {
    val in = Input(UInt(16.W))
    val out = Output(UInt(16.W))
  })
  setInline("BBAddOne.v",
  """
    |module BBAddOne(
    |    input  [15:0] in,
    |    output reg [15:0] out
    |);
    |  always @* begin
    |  out <= in + 1;
    |  end
    |endmodule
  """.stripMargin)
}

class BBAddTwo extends HasBlackBoxResource {
  val io = IO(new Bundle {
    val in = Input(UInt(16.W))
    val out = Output(UInt(16.W))
  })
  setResource("/AddTwoAddThree.v")
}

class BBAddThree extends HasBlackBoxResource {
  val io = IO(new Bundle {
    val in = Input(UInt(16.W))
    val out = Output(UInt(16.W))
  })
  setResource("/AddTwoAddThree.v")
}

class UsesBBAddOne extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(16.W))
    val out1 = Output(UInt(16.W))
    val out2 = Output(UInt(16.W))
    val out3 = Output(UInt(16.W))
  })
  val bbAddOne = Module(new BBAddOne)
  bbAddOne.io.in := io.in
  io.out1 := bbAddOne.io.out

  val bbAddTwo = Module(new BBAddTwo)
  bbAddTwo.io.in := io.in
  io.out2 := bbAddTwo.io.out

  val bbAddThree = Module(new BBAddThree)
  bbAddThree.io.in := io.in
  io.out3 := bbAddThree.io.out
}

class UsesBBAddOneTester(c: UsesBBAddOne) extends PeekPokeTester(c) {
  poke(c.io.in, 1)
  step(1)
  expect(c.io.out1, 2)
  expect(c.io.out2, 3)
  expect(c.io.out3, 4)
  step(1)
}

class BlackBoxVerilogDeliverySpec extends FreeSpec with Matchers {
  "blackbox verilog implementation should end up accessible to verilator" in {
    iotesters.Driver.execute(Array("--backend-name", "verilator"), () => new UsesBBAddOne) { c =>
      new UsesBBAddOneTester(c)
    } should be (true)
  }

  "blackbox verilog implementation should end up accessible to vcs" in {
    assume(firrtl.FileUtils.isVCSAvailable)
    val manager = new TesterOptionsManager {
      testerOptions = testerOptions.copy(backendName = "vcs")
    }
    iotesters.Driver.execute(() => new UsesBBAddOne, manager) { c =>
      new UsesBBAddOneTester(c)
    } should be(true)
    new java.io.File(
        manager.targetDirName, firrtl.transforms.BlackBoxSourceHelper.fileListName).exists() should be (true)
  }
}
