// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import org.scalatest._
import org.scalatest.prop._
import org.scalacheck._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks}
import chisel3._
import chisel3.testers._
import sys.process.{stringSeqToProcess, BasicIO}
import scala.util.Properties.envOrElse

/** Common utility functions for Chisel unit tests. */
@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
trait ChiselRunners extends Assertions {
  val backends = envOrElse("TESTER_BACKENDS", "firrtl") split " "
  def runTester(t: => BasicTester, additionalVResources: Seq[String] = Seq()): Boolean = {
    TesterDriver.execute(() => t, additionalVResources)
  }
  def assertTesterPasses(t: => BasicTester, additionalVResources: Seq[String] = Seq()): Unit = {
    assert(runTester(t, additionalVResources))
  }
  def elaborate(t: => Module): Unit = chisel3.stage.ChiselStage.elaborate(t)
}

/** Spec base class for BDD-style testers. */
@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
class ChiselFlatSpec extends AnyFlatSpec with ChiselRunners with Matchers

/** Spec base class for property-based testers. */
@deprecated("chisel-iotesters is end-of-life. Use chiseltest instead, see https://www.chisel-lang.org/chiseltest/migrating-from-iotesters.", "chisel-iotesters 2.5.0")
class ChiselPropSpec extends AnyPropSpec with ChiselRunners with ScalaCheckPropertyChecks {

  // Constrain the default number of instances generated for every use of forAll.
  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSuccessful = 8, minSize = 1, sizeRange = 3)

  // Generator for small positive integers.
  val smallPosInts = Gen.choose(1, 4)

  // Generator for widths considered "safe".
  val safeUIntWidth = Gen.choose(1, 30)

  // Generators for integers that fit within "safe" widths.
  val safeUInts = Gen.choose(0, (1 << 30))

  // Generators for vector sizes.
  val vecSizes = Gen.choose(0, 4)

  // Generator for string representing an arbitrary integer.
  val binaryString = for (i <- Arbitrary.arbitrary[Int]) yield "b" + i.toBinaryString

  // Generator for a sequence of Booleans of size n.
  def enSequence(n: Int): Gen[List[Boolean]] = Gen.containerOfN[List, Boolean](n, Gen.oneOf(true, false))

  // Generator which gives a width w and a list (of size n) of numbers up to w bits.
  def safeUIntN(n: Int): Gen[(Int, List[Int])] = for {
    w <- smallPosInts
    i <- Gen.containerOfN[List, Int](n, Gen.choose(0, (1 << w) - 1))
  } yield (w, i)

  // Generator which gives a width w and a numbers up to w bits.
  val safeUInt = for {
    w <- smallPosInts
    i <- Gen.choose(0, (1 << w) - 1)
  } yield (w, i)

  // Generator which gives a width w and a list (of size n) of a pair of numbers up to w bits.
  def safeUIntPairN(n: Int): Gen[(Int, List[(Int, Int)])] = for {
    w <- smallPosInts
    i <- Gen.containerOfN[List, Int](n, Gen.choose(0, (1 << w) - 1))
    j <- Gen.containerOfN[List, Int](n, Gen.choose(0, (1 << w) - 1))
  } yield (w, i zip j)

  // Generator which gives a width w and a pair of numbers up to w bits.
  val safeUIntPair = for {
    w <- smallPosInts
    i <- Gen.choose(0, (1 << w) - 1)
    j <- Gen.choose(0, (1 << w) - 1)
  } yield (w, i, j)
}
