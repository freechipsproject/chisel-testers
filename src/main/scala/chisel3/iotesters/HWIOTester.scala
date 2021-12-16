// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import chisel3.testers.BasicTester
import chisel3.{Bits, Module, printf}

import scala.util.Random

/**
  * provide common facilities for step based testing and decoupled interface testing
  */
@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
abstract class HWIOTester extends BasicTester {
  val device_under_test:     Module
  var io_info:               IOAccessor = null
  def finish():              Unit

  val rnd                    = Random  // convenience for writing tests

  var enable_scala_debug     = false
  var enable_printf_debug    = false
  var enable_all_debug       = false

  def logScalaDebug(msg: => String): Unit = {
    //noinspection ScalaStyle
    if(enable_all_debug || enable_scala_debug) println(msg)
  }

  def logPrintfDebug(fmt: String, args: Bits*): Unit = {
    if(enable_all_debug || enable_printf_debug) printf(fmt, args :_*)
  }
}
