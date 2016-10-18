// See LICENSE for license details.

package examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.LinkedHashMap

class PeekPokeBundleSpec extends FlatSpec with Matchers {
  // Define some data types to be used in the circuit.
  class ABundle extends Bundle {
    val aBool = Bool()

    // Since this bundle is defined within a class, we need an explicit cloneType method.
    override def cloneType: ABundle.this.type = new ABundle().asInstanceOf[ABundle.this.type]
  }

 class MyBundle extends Bundle {
   val aUInt4 = UInt(width = 4)
   val aSInt5 = SInt(width = 5)
   val aBundle = new ABundle()
   val aBottomBool = Bool()

   // Since this bundle is defined within a class, we need an explicit cloneType method.
   override def cloneType: MyBundle.this.type = new MyBundle().asInstanceOf[MyBundle.this.type]
 }

  // A trivial circuit that copies its input to its output.
  class MyCircuit extends Module {
    val io = IO( new Bundle {
      val in = Input(new MyBundle())
      val out = Output(new MyBundle())
    })
    io.out := io.in
  }

  // A tester for the trivial circuit.
  class BundlePeekPokeTester(dut: MyCircuit = new MyCircuit) extends PeekPokeTester(dut) {
    // If only we had Bundle literals ...
    // This is extremely fragile. The map definitions must match the order of element definitions in the Bundle
    //  we're about to peek or poke.
    var myBundleMap : LinkedHashMap[String, BigInt] = LinkedHashMap[String, BigInt]() ++ List[(String, BigInt)](
      ("aUInt4"	-> BigInt(3) ),
      ("aSInt5"	-> BigInt(2) ),
      ("aBundle.aBool"	-> BigInt(1) ),
      ("aBottomBool"	-> BigInt(0) )
    )
    poke(dut.io.in, myBundleMap.values.toArray)
    step(1)
    expect(dut.io.out, myBundleMap.values.toArray)
  }

  // The test.
  behavior of "PeekPokeBundleSpec"

  it should "poke and peek bundles" in {
    chisel3.iotesters.Driver(() => new MyCircuit) { c =>
      new BundlePeekPokeTester(c)
    } should be(true)
  }
}
