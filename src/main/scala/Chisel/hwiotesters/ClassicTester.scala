// See LICENSE for license details.

package Chisel.hwiotesters

// See LICENSE for license details.

import Chisel._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Use a UnitTester to construct a test harness for a chisel module
  * this module will be canonically referred to as the device_under_test, often simply as c in
  * a unit test, and also dut
  * The UnitTester is used to put series of values (as chisel.Vec's) into the ports of the dut io which are INPUT
  * At specified times it check the dut io OUTPUT ports to see that they match a specific value
  * The vec's are assembled through the following API
  * poke, expect and step, pokes
  *
  * @example
  * {{{
  *
  * class Adder(width:Int) extends Module {
  *   val io = new Bundle {
  *     val in0 : UInt(INPUT, width=width)
  *     val in1 : UInt(INPUT, width=width)
  *     val out : UInt(OUTPUT, width=width)
  *   }
  * }
  * class AdderTester extends UnitTester {
  *   val device_under_test = Module( new Adder(32) )
  *
  *   testBlock {
  *     poke(c.io.in0, 5)
  *     poke(c.io.in1, 7)
  *     expect(c.io.out, 12)
  *   }
  * }
  * }}}
  */
abstract class ClassicTester[T <: Module](val device_under_test: T) extends HWIOTester {
  val io_interface = device_under_test.io.flip()
  device_under_test.io <> io_interface

  def initIoInfo(): Unit = {
    if(io_info == null) {
      io_info = new IOAccessor(device_under_test.io)
    }
  }

  def poke(io_port: Data, value: Int): Unit = {
    initIoInfo()

    require(io_port.dir == INPUT, s"poke error: $io_port not an input")

    println(s"tester:poke:${name(io_port)} -> ${value}")
//    test_actions.last.input_map(io_port) = value
  }
  //  def poke(io_port: Data, bool_value: Boolean) = poke(io_port, if(bool_value) 1 else 0)

  def peek(io_port: Data, value: Int): Unit = {
    initIoInfo()

    require(io_port.dir == INPUT, s"poke error: $io_port not an input")

    println(s"tester:peek:${name(io_port)} -> ${value}")
    //    test_actions.last.input_map(io_port) = value
  }

  def expect(io_port: Data, value: Int): Unit = {
    initIoInfo()

    require(io_port.dir == OUTPUT, s"expect error: $io_port not an output")

    println(s"tester:expect:${name(io_port)} -> ${value}")
//    test_actions.last.output_map(io_port) = value
  }
  def expect(io_port: Data, bool_value: Boolean): Unit = expect(io_port, if(bool_value) 1 else 0)

  def step(number_of_cycles: Int): Unit = {
    initIoInfo()

    println(s"tester:step:$number_of_cycles")
//
//    test_actions ++= Array.fill(number_of_cycles) {
//      new Step(new mutable.HashMap[Data, Int](), new mutable.HashMap[Data, Int]())
//    }
  }

  private def name(port: Data): String = {
    "io." + io_info.port_to_name(port) + ":" + port.getWidth.toString
  }

  override def finish(): Unit = {
    stop()
//    io_info = new IOAccessor(device_under_test.io)
//
//    processEvents()
//
//    val pc             = Counter(test_actions.length)
//    val done           = Reg(init = Bool(false))
//
//    when(!done) {
//      io_info.dut_inputs.filter(io_info.ports_referenced.contains).foreach { port => createVectorsForInput(port, pc) }
//      io_info.dut_outputs.filter(io_info.ports_referenced.contains).foreach { port => createVectorsAndTestsForOutput(port, pc) }
//
//      when(pc.inc()) {
//        printf(s"Stopping, end of tests, ${test_actions.length} steps\n")
//        done := Bool(true)
//        stop()
//      }
//    }
    io_info.showPorts("".r)
  }
}
