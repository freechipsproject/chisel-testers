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

  def buildTestConditional(index: UInt): Bool = {
    val vec = Vec(value_list)
    port.toBits() === vec(index).toBits()
  }
}

case class IOVectorFactory(name: String) {
  var hash = new mutable.HashMap[Data, IOVectorGenerator[_]]()

  def apply[T <: Data](port: T, value: T, step: Int): Unit = {
    if(!hash.contains(port)) {
      hash(port) = IOVectorGenerator(port)
    }
    val poker = hash(port).asInstanceOf[IOVectorGenerator[T]]
    poker.add(step, value)
  }

  def portsUsed: Iterable[Data] = {
    hash.keys
  }
}


