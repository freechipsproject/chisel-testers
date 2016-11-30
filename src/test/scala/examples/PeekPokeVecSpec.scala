// See LICENSE for license details.

package scala.examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.LinkedHashMap

class PeekPokeVecSpec extends FlatSpec with Matchers {
  // Define some data types to be used in the circuit.
  class ABundle extends Bundle {
    val aBool = Bool()

    // Since this bundle is defined within a class, we need an explicit cloneType method.
    override def cloneType: ABundle.this.type = new ABundle().asInstanceOf[ABundle.this.type]
  }

 class MyVecBundle extends Bundle {
   val aVec = Vec(7, UInt(5.W))
   val aBundle = new ABundle()
   val aBottomBool = Bool()

   // Since this bundle is defined within a class, we need an explicit cloneType method.
   override def cloneType: MyVecBundle.this.type = new MyVecBundle().asInstanceOf[MyVecBundle.this.type]
 }

  // A trivial circuit that copies its input to its output.
  class MyCircuit extends Module {
    val io = IO( new Bundle {
      val in = Input(new MyVecBundle())
      val out = Output(new MyVecBundle())
    })
    io.out := io.in
  }

  // A tester for the trivial circuit.
  class BundlePeekPokeTesterVec(dut: MyCircuit = new MyCircuit) extends PeekPokeTester(dut) {
    // If only we had Bundle literals ...
    val myVecVals = Array[BigInt](1, 2, 3, 4, 5, 6, 7 )
    val myBundleMap : LinkedHashMap[String, BigInt] = LinkedHashMap[String, BigInt]() ++ List[(String, BigInt)](
      ("aBundle.aBool"	-> BigInt(0) ),
      ("aBottomBool"	-> BigInt(1) )
    )
    poke(dut.io.in, myBundleMap.toMap)
    poke(dut.io.in.aVec, myVecVals)
    step(1)
    expect(dut.io.out, myBundleMap.toMap)
    expect(dut.io.out.aVec, myVecVals)
  }

  // The test.
  behavior of "PeekPokeBundleVecSpec"

  it should "poke and peek bundles containing a Vec with a LinkedHashMap" in {
    chisel3.iotesters.Driver(() => new MyCircuit) { c =>
      new BundlePeekPokeTesterVec(c)
    } should be(true)
  }
}
