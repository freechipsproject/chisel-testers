// See LICENSE for license details.

package Chisel.hwiotesters

import Chisel._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class IOVectorGenerator[T <: Data](port: T) {
  val value_list = new mutable.HashMap[Int, T]()
  var max_step = 0

  def add(step: Int, value: T): Unit = {
    value_list(step) = value
    max_step = max_step.max(step)
  }
  def build(index: UInt): Unit = {
    val vec = Vec(
      (0 to max_step).map { step_number =>
        value_list.getOrElse(step_number, port.fromBits(Bits(0)))
      })
    port := vec(index)
  }
  def buildIsUsedVector: Vec[Bool] = {
    val vec = Vec(
      (0 to max_step).map { step_number =>
        Bool(value_list.contains(step_number))
      })
    vec
  }

  def buildTestConditional(index: UInt): Bool = {
    val vec = Vec(
      (0 to max_step).map { step_number =>
        value_list.getOrElse(step_number, port.fromBits(Bits(0)))
      })

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

  def apply[T <: Data](port: T): IOVectorGenerator[_] = {
    hash(port).asInstanceOf[IOVectorGenerator[T]]
  }

  def portsUsed: Iterable[Data] = {
    hash.keys
  }
}


