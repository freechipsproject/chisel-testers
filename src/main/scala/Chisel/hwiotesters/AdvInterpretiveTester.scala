/*
Copyright (c) 2014 - 2016 The Regents of the University of
California (Regents). All Rights Reserved.  Redistribution and use in
source and binary forms, with or without modification, are permitted
provided that the following conditions are met:
   * Redistributions of source code must retain the above
     copyright notice, this list of conditions and the following
     two paragraphs of disclaimer.
   * Redistributions in binary form must reproduce the above
     copyright notice, this list of conditions and the following
     two paragraphs of disclaimer in the documentation and/or other materials
     provided with the distribution.
   * Neither the name of the Regents nor the names of its contributors
     may be used to endorse or promote products derived from this
     software without specific prior written permission.
IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF
ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION
TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
MODIFICATIONS.
*/
package Chisel.hwiotesters
import Chisel._
import firrtl.interpreter._
import scala.collection.mutable.LinkedHashMap

/**
  * Created by chick on 4/30/16.
  */
class AdvInterpretiveTester(dutGenFunc: () => Module) {
  private val firrtlIR = Chisel.Driver.emit(dutGenFunc)
  protected val dut = Chisel.Driver.elaborateModule(dutGenFunc)
  private val (inputNodeInfoMap, outputNodeInfoMap) = getNodeInfo(dut)
  private val nodeToStringMap: Map[Data, String] = (inputNodeInfoMap.toList ++ outputNodeInfoMap.toList).map(x => (x._1, x._2._1)).toMap
  protected val rnd = new scala.util.Random(0)
  private val interpreter = FirrtlTerp(firrtlIR)
  interpreter.setInputUpdater(new EmptyUpdater())

  protected def bigIntToStr(x: BigInt, base: Int) = base match {
    case 2  if x < 0 => s"-0b${(-x).toString(base)}"
    case 16 if x < 0 => s"-0x${(-x).toString(base)}"
    case 2  => s"0b${x.toString(base)}"
    case 16 => s"0x${x.toString(base)}"
    case _ => x.toString(base)
  }

  def poke(name: String, value: BigInt): Unit = {
    assert(interpreter.hasInput(name))
    interpreter.sourceState.setInput(name, value)
    println(s"  POKE ${name} <- ${bigIntToStr(value, 16)}")
  }

  def peek(name: String, verbose: Boolean = true): BigInt = {
    interpreter.doCombinationalUpdate()
    var result: BigInt = -1
    interpreter.sourceState.getValue(name) match {
      case Some(ConcreteUInt(value, _)) => result = value
      case Some(ConcreteSInt(value, _)) => result = value
      case _ => throw new InterpreterException(s"Error:peek($name) value not found")
    }
    interpreter.sourceState.resetNameToConcreteValue()
    if (verbose) {
      println(s"  PEEK ${name} -> ${bigIntToStr(result, 16)}")
    }
    result
  }

  def expect(name: String, expectedValue: BigInt): Unit = {
    def testValue(value: BigInt): Unit = {
      println(s"  EXPECT ${name} -> ${bigIntToStr(value, 16)} == ${bigIntToStr(expectedValue, 16)}")
      println(s" ${if (value == expectedValue) "PASS" else "FAIL"}")
      if (value != expectedValue) {
        throw new InterpreterException (s"Error:expect($name, $expectedValue) got $value")
      }
    }
    testValue(peek(name, false))
  }

  def poke(signal: Bits, value: BigInt): Unit = {
    poke(nodeToStringMap(signal), value)
  }

  def peek(signal: Bits): BigInt = {
    peek(nodeToStringMap(signal))
  }

  def expect(signal: Bits, expectedValue: BigInt): Unit = {
    expect(nodeToStringMap(signal), expectedValue)
  }

  def step(n: Int = 1): Unit = {
    println(s"STEP ${n}")
    for(_ <- 0 until n) {
      interpreter.doOneCycle()
      interpreter.doCombinationalUpdate()
    }
  }
}

object getNodeInfo {
  def apply(dut: Module): (LinkedHashMap[Data, (String, Int)], LinkedHashMap[Data, (String, Int)]) = {
    val inputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()
    val outputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()

    def add_to_ports_by_direction(port: Data, name: String, width: Int): Unit = {
      port.dir match {
        case INPUT => inputNodeInfoMap(port) = (name, width)
        case OUTPUT => outputNodeInfoMap(port) = (name, width)
        case _ =>
      }
    }

    def parseBundle(b: Bundle, name: String = ""): Unit = {
      for ((n, e) <- b.namedElts) {
        val new_name = name + (if (name.length > 0) "_" else "") + n

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element => add_to_ports_by_direction(e, new_name, e.getWidth)
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }
    def parseVecs[T <: Data](b: Vec[T], name: String = ""): Unit = {
      for ((e, i) <- b.zipWithIndex) {
        val new_name = name + s"_$i"
        add_to_ports_by_direction(e, new_name, e.getWidth)

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element =>
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }

    parseBundle(dut.io, "io")
    (inputNodeInfoMap, outputNodeInfoMap)
  }
}
