// See LICENSE for license details.

package chisel3.iotesters

import scala.collection.mutable.ArrayBuffer

import chisel3.core._

/** Records and reports runtime errors and warnings. */
private[iotesters] class TestErrorLog {
  def hasErrors: Boolean = errors.exists(_.isFatal)

  /** Log an error message */
  def error(m: => String): Unit =
    errors += new Error(m)

  /** Log a warning message */
  def warning(m: => String): Unit =
    errors += new Warning(m)

  /** Log a deprecation warning message */
  def deprecated(m: => String): Unit =
    errors += new DeprecationWarning(m)

  /** Emit an informational message */
  def info(m: String): Unit =
    println(new Info("[%2.3f] %s".format(elapsedTime/1e3, m)))  // scalastyle:ignore regex

  /** Prints error messages generated by Chisel at runtime. */
  def report(): Unit = errors foreach println  // scalastyle:ignore regex

  /** Throw an exception if any errors have yet occurred. */
  def checkpoint(): Unit = {
    report()
    errors.clear()
  }

  private val errors = ArrayBuffer[LogEntry]()

  private val startTime = System.currentTimeMillis
  private def elapsedTime: Long = System.currentTimeMillis - startTime
}

private abstract class LogEntry(msg: => String) {
  def isFatal: Boolean = false
  def format: String

  override def toString: String = s"${format} ${msg}"

  protected def tag(name: String, color: String): String =
    s"[${color}${name}${Console.RESET}]"
}

private class Error(msg: => String) extends LogEntry(msg) {
  override def isFatal: Boolean = true
  def format: String = tag("error", Console.RED)
}

private class Warning(msg: => String) extends LogEntry(msg) {
  def format: String = tag("warn", Console.YELLOW)
}

private class DeprecationWarning(msg: => String) extends LogEntry(msg) {
  def format: String = tag("warn", Console.CYAN)
}

private class Info(msg: => String) extends LogEntry(msg) {
  def format: String = tag("info", Console.MAGENTA)
}
