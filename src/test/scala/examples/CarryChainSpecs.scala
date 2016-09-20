// See LICENSE for license details.

package examples

import org.scalatest.{Matchers, FlatSpec}

import chisel3._
import firrtl_interpreter.InterpretiveTester

class CarryBlockIfc(width:Int) extends Module {
  val io = new Bundle {
    val a = UInt(INPUT,width=width)
    val ci = UInt(INPUT,width=width)
    val co = UInt(OUTPUT,width=width)
  }
}

class ORBlock extends CarryBlockIfc(1) {
  io.co := io.a | io.ci
}

class CarryChainIfc(N:Int,width:Int) extends Module {
  val io = new Bundle {
    val a  = Vec( N, UInt(INPUT,width=width))
    val co = Vec( N, UInt(OUTPUT,width=width))
  }
}

class SimpleChainFunctionalBreaks(N:Int,width:Int, factory:() => CarryBlockIfc) extends CarryChainIfc(N,width) {
  require( io.a.length == io.co.length)
  require( io.a.length == N)
  println(s"SimpleChainFunctional: ${io.a.length} $N")
  io.co(0) := io.a(0)
  for( ((a,ci),co)<-io.a.toList.tail.zip( io.co).zip( io.co.tail)) {
    val b = Module( factory())
    b.io.a := a
    b.io.ci := ci
    co := b.io.co
  }
}

class SimpleChainFunctional(N:Int,width:Int, factory:() => CarryBlockIfc) extends CarryChainIfc(N,width) {
  require( io.a.length == io.co.length)
  require( io.a.length == N)
  println(s"SimpleChainFunctional: ${io.a.length} $N")
  io.co(0) := io.a(0)
  var nextCarryIn = io.a(0)
  for( ((a,ci),co)<-io.a.toList.tail.zip( io.co).zip( io.co.tail)) {
    val b = Module( factory())
    b.io.a := a
    b.io.ci := nextCarryIn
    nextCarryIn = b.io.co
    co := b.io.co
  }
}

class ORChain(N:Int)  extends SimpleChainFunctional(N,1,{()=>new ORBlock})



class ORChainTestAlt( val N:Int, factory:()=>CarryChainIfc) extends FlatSpec with Matchers {
  def v(bin: String): Array[BigInt] = {
    bin.toList.map( "01".indexOf(_)).map( BigInt(_)).reverse.toArray
  }

  def run( lst:List[(Array[BigInt],Array[BigInt])]) : Unit = {
    val s = chisel3.Driver.emit( factory)

    val tester = new InterpretiveTester(s)

    for ( (a,co) <- lst) {
      assert( N == a.length)
      assert( N == co.length)
      for( (y,idx) <- a.zipWithIndex) {
        tester.poke( s"io_a_$idx", y)
      }
      tester.step(1)
      for( (y,idx) <- co.zipWithIndex) {
        tester.expect( s"io_co_$idx", y)
      }
      tester.step(1)
    }

    tester.report()
  }

}

class ORChainTestAlt1 extends ORChainTestAlt(1,{()=>new ORChain(1)}) {
  val lst = List( (v("1"),v("1")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}
class ORChainTestAlt2 extends ORChainTestAlt(2,{()=>new ORChain(2)}) {
  val lst = List( (v("01"),v("11")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}
class ORChainTestAlt3 extends ORChainTestAlt(3,{()=>new ORChain(3)}) {
  val lst = List( (v("001"),v("111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}
class ORChainTestAlt4 extends ORChainTestAlt(4,{()=>new ORChain(4)}) {
  val lst = List( (v("0001"),v("1111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}
class ORChainTestAlt5 extends ORChainTestAlt(5,{()=>new ORChain(5)}) {
  val lst = List( (v("00001"),v("11111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}

class ORChainTestAlt6 extends ORChainTestAlt(6,{()=>new ORChain(6)}) {
  val lst = List( (v("000001"),v("111111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}

class ORChainTestAlt8 extends ORChainTestAlt(8,{()=>new ORChain(8)}) {
  val lst = List( (v("00000001"),v("11111111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}

class ORChainTestAlt12 extends ORChainTestAlt(12,{()=>new ORChain(12)}) {
  val lst = List( (v("000000000001"),v("111111111111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}

class ORChainTestAlt16 extends ORChainTestAlt(16,{()=>new ORChain(16)}) {
  val lst = List( (v("0000000000000001"),v("1111111111111111")))
  s"ORChainTestAlt with N=$N" should "work" in {
    run( lst)
  }
}
