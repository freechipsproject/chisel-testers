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

package chisel3.iotesters

import chisel3.testers.BasicTester
import chisel3.{Bits, Module, printf}

import scala.util.Random

/**
  * provide common facilities for step based testing and decoupled interface testing
  */
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
