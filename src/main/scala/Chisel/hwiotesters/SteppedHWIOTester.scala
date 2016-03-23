// See LICENSE for license details.

package Chisel.hwiotesters

import Chisel._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class IOVectorGenerator[T <: Data](port: T) {
  val value_list = new mutable.ArrayBuffer[T]()
  var max_step = 0

  def add(step: Int, value: T): Unit = {
    while(value_list.length <= step) value_list += port.fromBits(Bits(0))
    value_list(step) = value
    max_step = max_step.max(step)
  }
  def build(index: UInt): Unit = {
    val vec = Vec(value_list)
    port := vec(index)
  }
}

object IOVectorFactory {
  var hash = new mutable.HashMap[Data, IOVectorGenerator[_]]()

  def apply[T <: Data](port: T, value: T, step: Int): Unit = {
    if(!hash.contains(port)) {
      hash(port) = IOVectorGenerator(port)
    }
    val poker = hash(port).asInstanceOf[IOVectorGenerator[T]]
    poker.add(step, value)
  }

  def portsUsed = {
    hash.keys
  }
}

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

abstract class SteppedHWIOTester extends HWIOTester {
  type TesterMap = mutable.HashMap[Data,Bits]
  case class Step(input_map: TesterMap, output_map: TesterMap)

  // Scala stuff
  private val test_actions = new ArrayBuffer[Step]()
  var step_number = 0

  step(1) // gives us a slot to put in our input and outputs from beginning

  def poke(io_port: Data, value: Bundle): Unit = {
    IOVectorFactory(io_port, value, step_number)
  }

  def poke(io_port: Data, value: Int): Unit = {
    IOVectorFactory(io_port, Bits(value), step_number)
  }

  def poke(io_port: Data, value: Bits): Unit = {
    require(io_port.isInstanceOf[Bundle] || io_port.dir == INPUT, s"poke error: $io_port not an input")
    require(!test_actions.last.input_map.contains(io_port),
      s"second poke to $io_port without step\nkeys ${test_actions.last.input_map.keys.mkString(",")}")

    IOVectorFactory(io_port, value, step_number)
  }
//  def poke(io_port: Data, bool_value: Boolean) = poke(io_port, if(bool_value) 1 else 0)

  def expect(io_port: Data, value: Bundle): Unit = {
    expect(io_port, value.toBits())
  }
  def expect(io_port: Data, value: Int): Unit = {
    expect(io_port, Bits(value))
  }
  def expect(io_port: Data, value: Bits): Unit = {
    require(io_port.isInstanceOf[Bundle] || io_port.dir == OUTPUT, s"expect error: $io_port not an output")
    require(!test_actions.last.output_map.contains(io_port), s"second expect to $io_port without step")

//    println(s"expect port $io_port $bit_value")
    test_actions.last.output_map(io_port) = value
//    println(
//      test_actions.last.output_map.keys.map { k =>
//        val v = test_actions.last.output_map(k)
//        s"$k -> ${} $v  ${v.litValue()}"}.mkString("   ", "\n   ", ""))
  }
  def expect(io_port: Data, bool_value: Boolean): Unit = expect(io_port, if(bool_value) 1 else 0)

  def step(number_of_cycles: Int): Unit = {
    test_actions ++= Array.fill(number_of_cycles) {
      new Step(new TesterMap(), new TesterMap())
    }
    step_number += 1
  }

  private def name(port: Data): String = io_info.port_to_name(port)

  //noinspection ScalaStyle
  private def printStateTable(): Unit = {
    val default_table_width = 80

    if(io_info.ports_referenced.nonEmpty) {
//      val ordered_inputs = io_info.dut_inputs.filter(io_info.ports_referenced.contains).toList.sortWith { case (a, b) =>
      val ordered_inputs = IOVectorFactory.portsUsed.toList.sortWith { case (a, b) =>
        io_info.port_to_name(a) < io_info.port_to_name(b) }
      val ordered_outputs = io_info.dut_outputs.filter(io_info.ports_referenced.contains).toList.sortWith { case (a, b) =>
        io_info.port_to_name(a) < io_info.port_to_name(b) }

      val column_width_templates = (ordered_inputs ++ ordered_outputs).map { port =>
        val column_header = name(port)
        val data_width    = port.getWidth / 3
        port -> s"%${column_header.length.max(data_width)+2}s"
        }.toMap

      println("=" * default_table_width)
      println("UnitTester state table")
      println(
        "%6s".format("step") +
          ordered_inputs.map { dut_input => column_width_templates(dut_input).format(name(dut_input)) }.mkString +
          ordered_outputs.map { dut_output => column_width_templates(dut_output).format(name(dut_output)) }.mkString
      )
      println("-" * default_table_width)
      /**
        * prints out a table form of input and expected outputs
        */

      def val_str(hash: TesterMap, key: Data): String = {
        if (hash.contains(key)) "%s".format(hash(key).litValue()) else "-"
      }
      def get_str(port: Data, step: Int): String = {
        IOVectorFactory.hash(port).value_list(step).toString
      }

      test_actions.zipWithIndex.foreach { case (step, step_number) =>
        print("%6d".format(step_number))
        for (port <- ordered_inputs) {
          print(column_width_templates(port).format(get_str(port, step_number)))
        }
        for (port <- ordered_outputs) {
          print(column_width_templates(port).format(val_str(step.output_map, port)))
        }
        println()
      }
      println("=" * default_table_width)
    }
  }

  private def createVectorsForInput(input_port: Data, counter: Counter): Unit = {
    var default_value = Bits(0)
    val input_values = Vec(
      test_actions.map { step =>
        default_value = step.input_map.getOrElse(input_port, default_value).asUInt()
        default_value
      }
    )
//    input_values(counter.value) <> input_port
//    input_port := input_values(counter.value)
    IOVectorFactory.hash(input_port).build(counter.value)
  }

  private def createVectorsAndTestsForOutput(output_port: Data, counter: Counter): Unit = {
    val output_values = Vec(
      test_actions.map { step =>
        output_port.fromBits(step.output_map.getOrElse(output_port, Bits(0)))
      }
    )
    val ok_to_test_output_values = Vec(
      test_actions.map { step =>
        Bool(step.output_map.contains(output_port))
      }
    )

    when(ok_to_test_output_values(counter.value)) {
      when(output_port.toBits() === output_values(counter.value).toBits()) {
                  logPrintfDebug("    passed step %d -- " + name(output_port) + ":  %d\n",
                    counter.value,
                    output_port.toBits()
                  )
      }.otherwise {
        printf("    failed on step %d -- port " + name(output_port) + ":  %d expected %d\n",
          counter.value,
          output_port.toBits(),
          output_values(counter.value).toBits()
        )
        // TODO: Use the following line instead of the unadorned assert when firrtl parsing error issue #111 is fixed
        // assert(Bool(false), "Failed test")
        assert(Bool(false))
        stop()
      }
    }
  }

  private def processEvents(): Unit = {
    test_actions.foreach { case step =>
//      io_info.ports_referenced ++= step.input_map.keys
      io_info.ports_referenced ++= IOVectorFactory.portsUsed
      io_info.ports_referenced ++= step.output_map.keys
    }
  }

  override def finish(): Unit = {
    io_info = new IOAccessor(device_under_test.io)

    processEvents()

    val pc             = Counter(test_actions.length)
    val done           = Reg(init = Bool(false))

    when(!done) {
//      io_info.dut_inputs.filter(io_info.ports_referenced.contains).foreach { port => createVectorsForInput(port, pc) }
      IOVectorFactory.portsUsed.foreach { port => createVectorsForInput(port, pc) }
      io_info.dut_outputs.filter(io_info.ports_referenced.contains).foreach { port => createVectorsAndTestsForOutput(port, pc) }

      when(pc.inc()) {
        printf(s"Stopping, end of tests, ${test_actions.length} steps\n")
        done := Bool(true)
        stop()
      }
    }
    io_info.showPorts("".r)
    printStateTable()
  }
}
