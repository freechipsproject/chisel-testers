// See LICENSE for license details.

package examples

import org.scalatest.{ Matchers, FlatSpec}

import chisel3._
import chisel3.util._
import chisel3.iotesters._

import chisel3.experimental._

/**
  * The Accum tests below illustrate the following.
  * Black boxes generally contain some verilog code that is linked in to the generated circuit.
  * When running simulations with the firrtl interpreter, verilog black boxes cannot currently be run.
  * As a work around the interpreter allows the developer to construct a scala implementation of the black box.
  * In this example it is a scala implementation of a black box that maintains some
  * kind of state withing the black box.  In verilog this is done in registers, but in a scala implementation it
  * must be one some other way.  Here we maintain state in a some class variables. A scala black box implementation
  * is only active when it's execute or cycle method is called.  Because cycle does not provide the state of the inputs
  * if cycle needs to access them then the execute call should put those inputs in an accessible place.
  */

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

/**
  * This is an implementation of a black box whose verilog is contained inline in AccumBlackBox, an instance of this
  * class will be placed into a black box factory so that it can be passed properly to the firrtl interpreter
  * @param name black box name
  */
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

/**
  * The factor that will provide firrtl access to the implementations
  */
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

/**
  * The value of the accumulator in the black box implementation may have advanced several times
  * because of simulation warm-up.  We find the initial value and see that it advances as expected
  * @param c the dut
  */
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
    assume(firrtl.FileUtils.isVCSAvailable)
    chisel3.iotesters.Driver( () => new AccumBlackBoxWrapper, "vcs"){ c => new Accum_PeekPokeTester(c)} should be (true)
  }

}
