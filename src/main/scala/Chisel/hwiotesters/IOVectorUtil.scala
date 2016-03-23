// See LICENSE for license details.

package Chisel.hwiotesters

import Chisel._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class IOVectorGenerator[T <: Data](port: T) {
  val value_list = new mutable.HashMap[Int, T]()
  var max_step = 0

  def okToAdd(step: Int): Boolean = {
    ! value_list.contains(step)
  }
  def add(step: Int, value: T): Unit = {

    value_list(step) = value
    max_step = max_step.max(step)
  }
  def buildInputAssignment(index: UInt): Unit = {
    var defaultValue : T = port.fromBits(Bits(0))
    val vec = Vec(
      (0 to max_step).map { step_number =>
        defaultValue = value_list.getOrElse(step_number, defaultValue)
        defaultValue
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
  def buildExpectedVectors: Vec[UInt] = {
    val vec = Vec(
      (0 to max_step).map { step_number =>
        value_list.getOrElse(step_number, port.fromBits(Bits(0))).toBits()
      })
    vec
  }

  def buildTestConditional(index: UInt, vec: Vec[UInt]): Bool = {
    port.toBits() === vec(index)
  }

  def asString(step: Int): String = {
    if( ! value_list.contains(step) ) {
      "-"
    }
    else {
      val value = value_list(step)
      value.litArg() match {
        case Some(lit_arg) => lit_arg.num.toString()
        case _ => value.toString
      }
    }
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

  def asString[T <: Data](port: T, step: Int): String = {
    if(! hash.contains(port) ) "-"
    else {
      hash(port).asString(step)
    }
  }
}


