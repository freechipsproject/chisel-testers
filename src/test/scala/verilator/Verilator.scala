package verilator


import chisel3._
import chisel3.iotesters.chiselMain
import org.scalatest._
import chisel3._

class VerilatorTest extends FlatSpec with Matchers {
  "The Verilator backend" should "be able to compile the cpp code" in {
      val args = Array[String]("--v",
          "--backend",
          "verilator",
          "--compile",
          "--genHarness",
          "--minimumCompatibility", "3.0.0")
      chiselMain(args, () => new doohickey())
    }
}
