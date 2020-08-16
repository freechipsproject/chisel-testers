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

import chisel3._
import chisel3.experimental.Interval
import chisel3.internal.firrtl.IntervalRange
import org.scalatest.{FreeSpec, Matchers}

class IntervalShifter(val bitWidth: Int, val binaryPoint: Int, val fixedShiftSize: Int) extends Module {
  val dynamicShifterWidth = 3

  val io = IO(new Bundle {
    val inValue = Input(Interval(IntervalRange(bitWidth.W, binaryPoint.BP)))
    val dynamicShiftValue = Input(UInt(dynamicShifterWidth.W))
    val shiftRightResult: Option[Interval] = if(fixedShiftSize < bitWidth) {
      Some(Output(Interval(IntervalRange((bitWidth - fixedShiftSize).W, binaryPoint.BP))))
    }
    else {
      None
    }
    val shiftLeftResult = Output(Interval(IntervalRange((bitWidth + fixedShiftSize).W, binaryPoint.BP)))
    val dynamicShiftRightResult = Output(Interval(IntervalRange(bitWidth.W, binaryPoint.BP)))
    val dynamicShiftLeftResult = Output(
      Interval(IntervalRange((bitWidth + (1 << dynamicShifterWidth) - 1).W, binaryPoint.BP))
    )
  })

  io.shiftLeftResult := io.inValue << fixedShiftSize
  io.shiftRightResult.foreach { out =>
    out := (io.inValue >> fixedShiftSize).asInstanceOf[Interval].squeeze(out)
  }
  io.dynamicShiftLeftResult := io.inValue << io.dynamicShiftValue
  io.dynamicShiftRightResult := io.inValue >> io.dynamicShiftValue
}

class IntervalShiftLeftSpec extends FreeSpec with Matchers {
  "Shift left of interval used to create Dshlw problem in CheckTypes" in {
    val backendName = "treadle"
    val defaultWidth = 8
    val binaryPoint = 0
    val fixedShiftSize = 1
    Driver.execute(
      Array(
        "--backend-name", backendName,
        "--target-dir", s"test_run_dir/interval-shift-test-$fixedShiftSize-$binaryPoint.BP"
      ),
      () => new IntervalShifter(bitWidth = 8, binaryPoint = binaryPoint, fixedShiftSize = fixedShiftSize)

    ) { c =>
      new PeekPokeTester(c) {

      }
    } should be(true)
  }
}
