// See LICENSE for license details.

package chisel.iotesters

import chisel._

import scala.collection.mutable.LinkedHashMap
import scala.collection.immutable.ListMap

private[iotesters] object getPortNameMaps {
  def apply(dut: Module) = {
    // Node -> (firrtl name, IPC name)
    val inputMap = LinkedHashMap[Bits, (String, String)]()
    val outputMap = LinkedHashMap[Bits, (String, String)]()
    def loop(name: String, data: Data): Unit = data match {
      case b: Bundle => b.elements foreach {case (n, e) => loop(s"${name}_${n}", e)}
      case v: Vec[_] => v.zipWithIndex foreach {case (e, i) => loop(s"${name}_${i}", e)}
      case b: Bits if b.dir == INPUT => inputMap(b) = (name, s"${dut.name}.${name}")
      case b: Bits if b.dir == OUTPUT => outputMap(b) = (name, s"${dut.name}.${name}")
      case _ => // skip
    }
    loop("io", dut.io)
    (ListMap(inputMap.toList:_*), ListMap(outputMap.toList:_*))
  }
}

private[iotesters] object bigIntToStr {
  def apply(x: BigInt, base: Int) = base match {
    case 2  if x < 0 => s"-0b${(-x).toString(base)}"
    case 16 if x < 0 => s"-0x${(-x).toString(base)}"
    case 2  => s"0b${x.toString(base)}"
    case 16 => s"0x${x.toString(base)}"
    case _ => x.toString(base)
  }
}

private[iotesters] case class TestApplicationException(exitVal: Int, lastMessage: String) extends RuntimeException(lastMessage)
