//// See LICENSE for license details.
//
//package chisel3.iotesters
//
//import chisel3._
//import org.scalatest.{FreeSpec, Matchers}
//
//
///**
//  * Passes a Vec of elements with one cycle delay
//  * This part of an example of using poke on a Vector input
//  * @param numberOfElements  number of elements to be sorted
//  * @param elementGenerator  generator for kind of elements to be sorted
//  */
//class VecPassThrough(val numberOfElements: Int, elementGenerator: => UInt) extends Module {
//  val io = IO(new Bundle {
//    val inVector = Input(Vec(numberOfElements, elementGenerator))
//    val outVector = Output(Vec(numberOfElements, elementGenerator))
//    val outVectorAsUInt = Output(UInt(inVector.getWidth.W))
//  })
//
//  val regVector = Reg(Vec(numberOfElements, elementGenerator))
//
//  regVector <> io.inVector
//  io.outVector <> regVector
//
//  io.outVectorAsUInt := io.inVector.asUInt()
//}
//
///**
//  * Passes a Bundle of elements with one cycle delay
//  * This part of an example of using poke on a Vector input
//  */
//class BundlePassThrough extends Module {
//  val io = IO(new Bundle {
//    val inBundle = Input(new PassThroughBundle)
//    val outBundle = Output(new PassThroughBundle)
//    val outBundleAsUInt = Output(UInt(9.W))
//  })
//
//  val regBundle = Reg(new PassThroughBundle)
//
//  regBundle <> io.inBundle
//  io.outBundle <> regBundle
//
//  io.outBundleAsUInt := io.inBundle.asUInt()
//}
//
//class PassThroughBundle extends Bundle {
//  val u1 = UInt(3.W)
//  val u2 = UInt(9.W)
//  val u3 = UInt(27.W)
//}
//
///**
//  * Demonstrate that calling poke with a IndexedSeq of BigInts
//  * will poke the individual elements of a Vec
//  *
//  * @param c is the device under test
//  */
//class VecPeekPokeTester(c: VecPassThrough) extends PeekPokeTester(c) {
//  private val numberOfElements = c.numberOfElements
//
//  private val vectorInputs = Array.tabulate(numberOfElements) { x => BigInt(x) }
//  println(s"scala array to poke into vector    ${vectorInputs.mkString(",")}")
//
//  poke(c.io.inVector, vectorInputs)
//
//  private val allAtOncePeekedInputs = peek(c.io.inVector)
//  println(s"input peeked all at once           ${allAtOncePeekedInputs.mkString(",")}")
//
//  private val individualyPeekedInputs = vectorInputs.indices.map { index => peek(c.io.inVector(index)) }
//  println(s"input peeked individually          ${individualyPeekedInputs.mkString(",")}")
//
//  vectorInputs.zipWithIndex.foreach { case (value, index) =>
//    expect(c.io.inVector(numberOfElements - (index + 1)), value)
//  }
//
//  step(1)
//
//  private val allAtOncePeekedOutputs = vectorInputs.indices.map { index => peek(c.io.outVector(index)) }
//  println(s"output peeked all at once          ${allAtOncePeekedOutputs.mkString(",")}")
//
//  private val individualyPeekedOutputs = vectorInputs.indices.map { index => peek(c.io.inVector(index)) }
//  println(s"output peeked individually         ${individualyPeekedOutputs.mkString(",")}")
//}
//
///**
//  * Demonstrate that calling poke with a IndexedSeq of BigInts
//  * will poke the individual elements of a Vec
//  *
//  * @param c is the device under test
//  */
//class BundlePeekPokeTester(c: BundlePassThrough) extends PeekPokeTester(c) {
//  private val numberOfElements = 3
//
//  private val vectorInputs = Array.tabulate(numberOfElements) { x => BigInt(x + 1) }
//  println(s"scala array to poke into vector    ${vectorInputs.mkString(",")}")
//
//  poke(c.io.inBundle, vectorInputs)
//
//  private val allAtOncePeekedInputs = peek(c.io.inBundle)
//  println(s"input peeked all at once           ${allAtOncePeekedInputs.mkString(",")}")
//
//  private val individualyPeekedInputs = Array(peek(c.io.inBundle.u1), peek(c.io.inBundle.u2), peek(c.io.inBundle.u3))
//  println(s"input peeked individually          ${individualyPeekedInputs.mkString(",")}")
//
//  step(1)
//
//  private val allAtOncePeekedOutputs = peek(c.io.outBundle)
//  println(s"output peeked all at once          ${allAtOncePeekedOutputs.mkString(",")}")
//
//  private val individualyPeekedOutputs = Array(peek(c.io.inBundle.u1), peek(c.io.inBundle.u2), peek(c.io.inBundle.u3))
//  println(s"output peeked individually         ${individualyPeekedOutputs.mkString(",")}")
//}
//
//class VectorPeekPokeSpec extends FreeSpec with Matchers {
//  "Poking vectors should be same as poking all elements" in {
//    iotesters.Driver.execute(() => new VecPassThrough(10, UInt(16.W)), new TesterOptionsManager) { c =>
//      new VecPeekPokeTester(c)
//    } should be(true)
//  }
//  "Poking bundles should be same as poking all elements" in {
//    iotesters.Driver.execute(() => new BundlePassThrough, new TesterOptionsManager) { c =>
//      new BundlePeekPokeTester(c)
//    } should be(true)
//  }
//}
