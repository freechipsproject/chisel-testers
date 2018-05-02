// See LICENSE for license details.
package chisel3.iotesters

import java.io.PrintStream

import chisel3._
import chisel3.internal.InstanceId

sealed trait Statement {
  def serialize: String
}

case class PokeStatement(signal: String, value: BigInt) extends Statement {
  def serialize = s"poke $signal : $value"
}
case class ExpectStatement(signal: String, value: BigInt, msg: String) extends Statement {
  val msgReplaced = msg.replace("\n", ":")
  def serialize = s"expect $signal : $value : $msgReplaced"
}
case class StepStatement(n: Int) extends Statement {
  def serialize = s"step $n"
}
case class ResetStatement(n: Int) extends Statement {
  def serialize = s"reset $n"
}

class IntermediateBackend(
    dut: Module,
    optionsManager: TesterOptionsManager = new TesterOptionsManager)
  extends Backend(_seed = System.currentTimeMillis())
{

  val portNames = getDataNames("io", dut.io).toMap

  val statements = scala.collection.mutable.Queue[Statement]()

  def poke(signal: InstanceId, value: BigInt, off: Option[Int])
          (implicit logger: PrintStream, verbose: Boolean, base: Int): Unit = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        poke(name, value)
      case _ =>
    }
          }

  def peek(signal: InstanceId, off: Option[Int])
          (implicit logger: PrintStream, verbose: Boolean, base: Int): BigInt = {
    throw new Exception("Peek not supported!")
  }

  def poke(path: String, value: BigInt)
          (implicit logger: PrintStream, verbose: Boolean, base: Int): Unit = {
    statements += PokeStatement(path, value)
  }

  def peek(path: String)
          (implicit logger: PrintStream, verbose: Boolean, base: Int): BigInt = {
    throw new Exception("Peek not supported!")
  }

  def expect(signal: InstanceId, expected: BigInt, msg: => String)
            (implicit logger: PrintStream, verbose: Boolean, base: Int): Boolean = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        expect(name, expected, msg)
      case _ =>
        false
    }
  }

  def expect(path: String, expected: BigInt, msg: => String)
            (implicit logger: PrintStream, verbose: Boolean, base: Int): Boolean = {
    statements += ExpectStatement(path, expected, msg)
    true
  }

  def step(n: Int)(implicit logger: PrintStream): Unit = {
    statements += StepStatement(n)
  }

  def reset(n: Int): Unit = {
    statements += ResetStatement(n)
  }

  def finish(implicit logger: PrintStream): Unit = {
    optionsManager.testerOptions.intermediateReportFunc(statements)
  }

}

private[iotesters] object setupIntermediateBackend
{
  def apply[T <: chisel3.Module](
      dutGen: () => T,
      optionsManager: TesterOptionsManager = new TesterOptionsManager): (T, Backend) =
  {
    chisel3.Driver.execute(optionsManager, dutGen) match {
      case ChiselExecutionSuccess(Some(circuit), firrtlText, Some(firrtlExecutionResult)) =>
        val dut = getTopModule(circuit).asInstanceOf[T]
        (dut, new IntermediateBackend(dut, optionsManager = optionsManager))
      case _ =>
        throw new Exception("Problem with compilation")
    }
  }
}

