/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package examples

import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import chisel3.util._
import chisel3.iotesters._
import chisel3.experimental._
import treadle.{ScalaBlackBox, ScalaBlackBoxFactory}
import treadle.executable.{PositiveEdge, Transition}

/**
  * The Accumulator tests below illustrate the following.
  * Black boxes generally contain some verilog code that is linked in to the generated circuit.
  * When running simulations with the firrtl interpreter, verilog black boxes cannot currently be run.
  * As a work around the interpreter allows the developer to construct a scala implementation of the black box.
  * In this example it is a scala implementation of a black box that maintains some
  * kind of state withing the black box.  In verilog this is done in registers, but in a scala implementation it
  * must be one some other way.  Here we maintain state in a some class variables. A scala black box implementation
  * is only active when it's execute or cycle method is called.  Because cycle does not provide the state of the inputs
  * if cycle needs to access them then the execute call should put those inputs in an accessible place.
  */

trait AccumulatorAbstractInterface extends BaseModule {
  val io = IO{
    new Bundle{
      val data = Output(UInt(16.W))
      val clock = Input(Clock())
    }
  }
}

class AccumulatorInterface extends Module with AccumulatorAbstractInterface

import firrtl.ir.Type
import firrtl_interpreter._

/**
  * This is an implementation of a black box whose verilog is contained inline in AccumulatorBlackBox, an instance of this
  * class will be placed into a black box factory so that it can be passed properly to the firrtl interpreter
  * @param name black box name
  */
class AccumulatorFirrtlInterpreterBlackBox( val name : String) extends BlackBoxImplementation with ScalaBlackBox {

  var ns : BigInt = 0
  var ps : BigInt = 0

  def outputDependencies(outputName: String): Seq[String] = {
    outputName match {
      case "data" => Seq("clock")
      case _ => Seq.empty
    }
  }

  def cycle(): Unit = {
    ps = ns
  }

  override def clockChange(transition: Transition, clockName: String): Unit = {
    transition match {
      case PositiveEdge =>
        ps = ns
        ns = ps + 1
        println(s"blackbox:$name ps $ps ns $ns")
      case _ =>
        println(s"not positive edge, no action for cycle in $name")
    }
  }
  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    ns = ps + 1
    TypeInstanceFactory(tpe, ps)
  }

  override def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    ps
  }
}

/**
  * The factor that will provide firrtl access to the implementations
  */
class AccumulatorBlackBoxFactory extends BlackBoxFactory {

  def createInstance(instanceName: String, blackBoxName: String): Option[BlackBoxImplementation] = {
    println( s"createInstance: $instanceName $blackBoxName")
    blackBoxName match {
      case "AccumulatorBlackBox" => Some(add(new AccumulatorFirrtlInterpreterBlackBox(instanceName)))
      case _               => None
    }
  }
}

class AccumulatorScalaBlackBoxFactory extends ScalaBlackBoxFactory {

  def createInstance(instanceName: String, blackBoxName: String): Option[ScalaBlackBox] = {
    blackBoxName match {
      case "AccumulatorBlackBox" => Some(add(new AccumulatorFirrtlInterpreterBlackBox(instanceName)))
      case _               => None
    }
  }
}

class AccumulatorBlackBox extends BlackBox with HasBlackBoxInline with AccumulatorAbstractInterface {
  setInline("AccumulatorBlackBox.v",
    s"""|module AccumulatorBlackBox( input clock, output [15:0] data);
        |  reg [15:0] ps;
        |  assign data = ps;
        |  always @(posedge clock) begin ps = ps + 1; end
        |  initial begin ps = 0; end
        |endmodule // AccumulatorBlackBox""".stripMargin)
}


class AccumulatorBlackBoxWrapper extends AccumulatorInterface {
  // Can't have black box at top level
  val m = Module(new AccumulatorBlackBox)
  io.data := m.io.data
  m.io.clock := clock
  // printf("m.io.data %d io.data %d\n", m.io.data, io.data)
}

/**
  * The value of the accumulator in the black box implementation may have advanced several times
  * because of simulation warm-up.  We find the initial value and see that it advances as expected
  * @param c the dut
  */
class AccumulatorPeekPokeTester[T <: AccumulatorInterface](c:T) extends PeekPokeTester(c) {
  val initialValue: BigInt = peek( c.io.data)
  step(1)

  expect( c.io.data, initialValue + 1)
  step(1)
  expect( c.io.data, initialValue + 2)
  step(1)
  expect( c.io.data, initialValue + 3)
  step(1)

}

class AccumulatorBlackBoxPeekPokeTest extends FlatSpec with Matchers {

  def getOptionsManager(backendName: String): TesterOptionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumulatorBlackBoxFactory
    )
    treadleOptions = treadleOptions.copy(
      blackBoxFactories = treadleOptions.blackBoxFactories :+ new AccumulatorScalaBlackBoxFactory,
      setVerbose = false
    )
    testerOptions = testerOptions.copy(backendName = backendName)
  }

  behavior of "AccumulatorBlackBox"

  it should "work with treadle" in {
    chisel3.iotesters.Driver.execute( () => new AccumulatorBlackBoxWrapper, getOptionsManager("treadle")){ c =>
      new AccumulatorPeekPokeTester(c)
    } should be (true)
  }

  it should "work with firrtl" in {
    chisel3.iotesters.Driver.execute( () => new AccumulatorBlackBoxWrapper, getOptionsManager("firrtl")){ c =>
      new AccumulatorPeekPokeTester(c)
    } should be (true)
  }

}

class AccumulatorBlackBoxPeekPokeTestVerilator extends FlatSpec with Matchers {

  val optionsManager: TesterOptionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumulatorBlackBoxFactory)
    treadleOptions = treadleOptions.copy(
      blackBoxFactories = treadleOptions.blackBoxFactories :+ new AccumulatorScalaBlackBoxFactory
    )
  }

  behavior of "AccumulatorBlackBox"

  it should "work" in {
    chisel3.iotesters.Driver( () => new AccumulatorBlackBoxWrapper, "verilator"){ c =>
      new AccumulatorPeekPokeTester(c)
    } should be (true)
  }

}

class AccumulatorBlackBoxPeekPokeTestVCS extends FlatSpec with Matchers {

  val optionsManager: TesterOptionsManager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(
      blackBoxFactories = interpreterOptions.blackBoxFactories :+ new AccumulatorBlackBoxFactory
    )
    treadleOptions = treadleOptions.copy(
      blackBoxFactories = treadleOptions.blackBoxFactories :+ new AccumulatorScalaBlackBoxFactory
    )
  }

  behavior of "AccumulatorBlackBox"

  it should "work" in {
    assume(firrtl.FileUtils.isVCSAvailable)
    chisel3.iotesters.Driver( () => new AccumulatorBlackBoxWrapper, "vcs"){ c =>
      new AccumulatorPeekPokeTester(c)
    } should be (true)
  }

}
