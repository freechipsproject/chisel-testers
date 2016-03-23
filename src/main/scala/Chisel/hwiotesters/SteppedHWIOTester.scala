// See LICENSE for license details.

package Chisel.hwiotesters

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
  *   poke(c.io.in0, 5)
  *   poke(c.io.in1, 7)
  *   expect(c.io.out, 12)
  * }
  * }}}
  */

abstract class SteppedHWIOTester extends HWIOTester {
  type TesterMap = mutable.HashMap[Data,Bits]
  case class Step(input_map: TesterMap, output_map: TesterMap)

  val input_vector_factory = IOVectorFactory("input")
  val output_vector_factory = IOVectorFactory("output")

  var step_number = 0

  step(1) // gives us a slot to put in our input and outputs from beginning


  def checkPoke(io_port: Data): Unit = {
    require(io_port.isInstanceOf[Bundle] || io_port.dir == INPUT, s"poke error: $io_port not an input")
    require(!input_vector_factory.hash.contains(io_port) ||
            (input_vector_factory.hash.contains(io_port) && input_vector_factory(io_port).okToAdd(step_number)),
      s"second poke to $io_port at step ${step_number}\nkeys ${input_vector_factory(io_port).value_list(step_number)}")
  }
  def poke(io_port: Data, value: Bundle): Unit = {
    checkPoke(io_port)
    input_vector_factory(io_port, value, step_number)
  }

  def poke(io_port: Data, value: Int): Unit = {
    checkPoke(io_port)
    input_vector_factory(io_port, Bits(value), step_number)
  }

  def poke(io_port: Data, value: Bits): Unit = {
    checkPoke(io_port)
    input_vector_factory(io_port, value, step_number)
  }

  def checkExpect(io_port: Data): Unit = {
    require(io_port.isInstanceOf[Bundle] || io_port.dir == OUTPUT, s"expect error: $io_port not an output")
    require(! output_vector_factory.hash.contains(io_port) ||
      (output_vector_factory.hash.contains(io_port) && output_vector_factory(io_port).okToAdd(step_number)),
      s"second expect on $io_port at ${step_number}\nkeys ${output_vector_factory(io_port).value_list(step_number)}")

  }
  def expect(io_port: Bundle, value: Bundle): Unit = {
    output_vector_factory(io_port, value, step_number)
  }
  def expect(io_port: Data, value: Int): Unit = {
    output_vector_factory(io_port, UInt(value), step_number)
  }
  def expect(io_port: Data, value: Bits): Unit = {
    checkExpect(io_port)
    output_vector_factory(io_port, value, step_number)
  }
  def expect(io_port: Data, bool_value: Boolean): Unit = expect(io_port, if(bool_value) 1 else 0)

  def step(number_of_cycles: Int): Unit = {
    step_number += 1
  }

  private def name(port: Data): String = io_info.port_to_name(port)

  //noinspection ScalaStyle
  private def printStateTable(): Unit = {
    val default_table_width = 80

    if(io_info.ports_referenced.nonEmpty) {
      val ordered_inputs = input_vector_factory.portsUsed.toList.sortWith { case (a, b) =>
        io_info.port_to_name(a) < io_info.port_to_name(b)
      }
      val ordered_outputs = output_vector_factory.portsUsed.toList.sortWith { case (a, b) =>
        io_info.port_to_name(a) < io_info.port_to_name(b)
      }

      def compute_widths(ordered_ports: Seq[Data], factory: IOVectorFactory): Map[Data, String] = {
        ordered_ports.map { port =>
          val column_header_width = name(port).length
          val data_width = factory(port).value_list.map { value => value.toString.length }.max
          val column_width = column_header_width.max(data_width)
          port -> s"%${column_width}s"
        }.toMap
      }
      val column_width_templates = compute_widths(ordered_inputs, input_vector_factory) ++
        compute_widths(ordered_outputs, output_vector_factory)

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
      def get_in_str(port: Data, step: Int): String = {
        input_vector_factory.hash(port).value_list.getOrElse(step, "-").toString
      }
      def get_out_str(port: Data, step: Int): String = {
        output_vector_factory(port).value_list.getOrElse(step, "-").toString
      }

      for( step <- 0 to step_number) {
        print("%6d".format(step))
        for (port <- ordered_inputs) {
          print(column_width_templates(port).format(get_in_str(port, step)))
        }
        for (port <- ordered_outputs) {
          print(column_width_templates(port).format(get_out_str(port, step)))
        }
        println()
      }
      println("=" * default_table_width)
    }
  }

  private def createVectorsForInput(input_port: Data, counter: Counter): Unit = {
    input_vector_factory.hash(input_port).build(counter.value)
  }

  private def createVectorsAndTestsForOutput(output_port: Data, counter: Counter): Unit = {
    val ok_to_test_output_values = output_vector_factory(output_port).buildIsUsedVector
    val test_vectors = output_vector_factory(output_port).buildExpectedVectors

    when(ok_to_test_output_values(counter.value)) {
      when(output_vector_factory(output_port).buildTestConditional(counter.value, test_vectors)) {
                  logPrintfDebug("    passed step %d -- " + name(output_port) + ":  %d\n",
                    counter.value,
                    output_port.toBits()
                  )
      }.otherwise {
        printf("    failed on step %d -- port " + name(output_port) + ":  %d expected %d\n",
          counter.value,
          output_port.toBits(),
          test_vectors(counter.value)
        )
        assert(Bool(false))
        stop()
      }
    }
  }

  private def processEvents(): Unit = {
    io_info.ports_referenced ++= input_vector_factory.portsUsed
    io_info.ports_referenced ++= output_vector_factory.portsUsed
  }

  override def finish(): Unit = {
    io_info = new IOAccessor(device_under_test.io)

    processEvents()

    val pc             = Counter(step_number)
    val done           = Reg(init = Bool(false))

    when(!done) {
      input_vector_factory.portsUsed.foreach { port => createVectorsForInput(port, pc) }
      output_vector_factory.portsUsed.foreach { port => createVectorsAndTestsForOutput(port, pc) }

      when(pc.inc()) {
        printf(s"Stopping, end of tests, $step_number steps\n")
        done := Bool(true)
        stop()
      }
    }
    io_info.showPorts("".r)
    printStateTable()
  }
}
