// See LICENSE for license details.

package examples

import org.scalatest.{ Matchers, FlatSpec}

import chisel3._
import chisel3.util._
import chisel3.iotesters._

import chisel3.experimental._

trait AccumAbstractIfc extends BaseModule {
  val io = IO{
    new Bundle{
      val data = Output(UInt(16.W))
      val clock = Input(Clock())
    }
  }
}

class AccumIfc extends Module with AccumAbstractIfc

import firrtl.ir.Type
import firrtl_interpreter._

class AccumFirrtlInterpreterBlackBox( val name : String) extends BlackBoxImplementation {

  var ns : BigInt = 0
  var ps : BigInt = 0

  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "data" => Seq()
      case _ => Seq.empty
    }
  }

  def cycle(): Unit = {
    ps = ns
  }

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    ns = ps + 1
    TypeInstanceFactory(tpe, ps)
  }
}

class AccumBlackBoxFactory extends BlackBoxFactory {

  def createInstance(instanceName: String, blackBoxName: String): Option[BlackBoxImplementation] = {
    println( s"createInstance: $instanceName $blackBoxName")
    blackBoxName match {
      case "AccumBlackBox" => Some(add(new AccumFirrtlInterpreterBlackBox(instanceName)))
      case _               => None
    }
  }
}

class AccumBlackBox extends BlackBox with HasBlackBoxInline with AccumAbstractIfc {
  setInline("AccumBlackBox.v",
    s"""|module AccumBlackBox( input clock, output [15:0] data);
        |  reg [15:0] ps;
        |  assign data = ps;
        |  always @(posedge clock) begin ps = ps + 1; end
        |  initial begin ps = 0; end
        |endmodule // AccumBlackBox""".stripMargin)
}


class AccumBlackBoxWrapper extends AccumIfc {
  // Can't have black box at top level
  val m = Module(new AccumBlackBox)
  io.data := m.io.data
  m.io.clock := clock
  printf("m.io.data %d io.data %d\n", m.io.data, io.data)
}

class Accum_PeekPokeTester[T <: AccumIfc](c:T) extends PeekPokeTester(c) {
  val initialValue: BigInt = peek( c.io.data)
  step(1)

  expect( c.io.data, initialValue + 1)
  step(1)
  expect( c.io.data, initialValue + 2)
  step(1)
  expect( c.io.data, initialValue + 3)
  step(1)

}

class AccumBlackBox_PeekPokeTest extends FlatSpec with Matchers {

  val optionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumBlackBoxFactory)
  }

  behavior of "AccumBlackBox"

  it should "work" in {
    chisel3.iotesters.Driver.execute( () => new AccumBlackBoxWrapper, optionsManager){ c => new Accum_PeekPokeTester(c)} should be (true)
  }

}

class AccumBlackBox_PeekPokeTest_Verilator extends FlatSpec with Matchers {

  val optionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumBlackBoxFactory)
  }

  behavior of "AccumBlackBox"

  it should "work" in {
    chisel3.iotesters.Driver( () => new AccumBlackBoxWrapper, "verilator"){ c => new Accum_PeekPokeTester(c)} should be (true)
  }

}

class AccumBlackBox_PeekPokeTest_VCS extends FlatSpec with Matchers {

  val optionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumBlackBoxFactory)
  }

  behavior of "AccumBlackBox"

  it should "work" in {
    chisel3.iotesters.Driver( () => new AccumBlackBoxWrapper, "vcs"){ c => new Accum_PeekPokeTester(c)} should be (true)
  }

}
