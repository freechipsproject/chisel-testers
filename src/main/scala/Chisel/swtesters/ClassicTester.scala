// See LICENSE for license details.

package Chisel.swtesters

import Chisel._

import scala.collection.mutable.{HashMap, ArrayBuffer}
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, _}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.Random
import java.nio.channels.FileChannel

// Provides a template to define tester transactions
trait ClassicTests {
  type DUT <: Module
  def dut: DUT
  def t: Long
  def rnd: Random
  implicit def int(x: Boolean): BigInt
  implicit def int(x: Int):     BigInt
  implicit def int(x: Long):    BigInt
  implicit def int(x: Bits):    BigInt
  def step(n: Int): Unit
  def poke(data: Bits, x: BigInt): Unit
  def pokeAt[T <: Bits](data: Mem[T], value: BigInt, off: Int): Unit
  def peek(data: Bits): BigInt
  def peekAt[T <: Bits](data: Mem[T], off: Int): BigInt
  def expect(good: Boolean, msg: => String): Boolean
  def expect(data: Bits, expected: BigInt, msg: => String = ""): Boolean
}

abstract class ClassicTester[+T <: Module](val dut: T, isTrace: Boolean = true) {
  private val _nameMap = HashMap[Data, String]()
  private val (_inputs, _outputs) = {
    def genChunk(arg: (Bits, String)) = arg match {case (io, n) =>
      _nameMap(io) = s"${dut.name}.${n}" ; io -> ((io.getWidth-1)/64 + 1) }
    (chiselMain.context.inputMap map genChunk, chiselMain.context.outputMap map genChunk) 
  } 
  private val _pokeMap = HashMap[Bits, BigInt]()
  private val _peekMap = HashMap[Bits, BigInt]()
  private val _signalMap = HashMap[String, Int]()
  private val _chunks = HashMap[String, Int]()
  private val _logs = ArrayBuffer[String]()

  private object SIM_CMD extends Enumeration {
    val RESET, STEP, UPDATE, POKE, PEEK, FORCE, GETID, GETCHK, SETCLK, FIN = Value }
  implicit def cmdToId(cmd: SIM_CMD.Value) = cmd.id
  implicit def longToInt(x: Long) = x.toInt

  class Channel(name: String) {
    private lazy val file = new java.io.RandomAccessFile(name, "rw")
    private lazy val channel = file.getChannel
    @volatile private lazy val buffer = {
      /* We have seen runs where buffer.put(0,0) fails with:
[info]     java.lang.IndexOutOfBoundsException:
[info]     at java.nio.Buffer.checkIndex(Buffer.java:532)
[info]     at java.nio.DirectByteBuffer.put(DirectByteBuffer.java:300)
[info]     at Chisel.Tester$Channel.release(Tester.scala:148)
[info]     at Chisel.Tester.start(Tester.scala:717)
[info]     at Chisel.Tester.<init>(Tester.scala:743)
[info]     at ArbiterSuite$ArbiterTests$8.<init>(ArbiterTest.scala:396)
[info]     at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]     at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]     at Chisel.Driver$.apply(Driver.scala:65)
[info]     at Chisel.chiselMain$.apply(hcl.scala:63)
[info]     ...
       */
      val size = channel.size
      assert(size > 16, "channel.size is bogus: %d".format(size))
      channel map (FileChannel.MapMode.READ_WRITE, 0, size)
    }
    implicit def intToByte(i: Int) = i.toByte
    val channel_data_offset_64bw = 4    // Offset from start of channel buffer to actual user data in 64bit words.
    def aquire {
      buffer put (0, 1)
      buffer put (2, 0)
      while((buffer get 1) == 1 && (buffer get 2) == 0) {}
    }
    def release { buffer put (0, 0) }
    def ready = (buffer get 3) == 0
    def valid = (buffer get 3) == 1
    def produce { buffer put (3, 1) }
    def consume { buffer put (3, 0) }
    def update(idx: Int, data: Long) { buffer putLong (8 * idx + channel_data_offset_64bw, data) }
    def update(base: Int, data: String) {
      data.zipWithIndex foreach {case (c, i) => buffer put (base + i + channel_data_offset_64bw, c) }
      buffer put (base + data.size + channel_data_offset_64bw, 0)
    }
    def apply(idx: Int): Long = buffer getLong (8 * idx + channel_data_offset_64bw)
    def close { file.close }
    buffer order java.nio.ByteOrder.nativeOrder
    new java.io.File(name).delete
  }

  /****************************/
  /*** Simulation Interface ***/
  /****************************/
  case class TestApplicationException(exitVal: Int, lastMessage: String) extends RuntimeException(lastMessage)

  private def throwExceptionIfDead(exitValue: Future[Int]) {
    if (exitValue.isCompleted) {
      val exitCode = Await.result(exitValue, Duration(-1, SECONDS))
      // We assume the error string is the last log entry.
      val errorString = if (_logs.size > 0) {
        _logs.last
      } else {
        "test application exit"
      } + " - exit code %d".format(exitCode)
      throw new TestApplicationException(exitCode, errorString)
    }
  }
  // A busy-wait loop that monitors exitValue so we don't loop forever if the test application exits for some reason.
  private def mwhile(block: => Boolean)(loop: => Unit) {
    while (!exitValue.isCompleted && block) {
      loop
    }
    // If the test application died, throw a run-time error.
    throwExceptionIfDead(exitValue)
  }

  private def sendCmd(data: Int) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }

  private def sendCmd(data: String) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }

  private def recvResp = {
    outChannel.aquire
    val valid = outChannel.valid
    val resp = if (!valid) None else {
      outChannel.consume
      Some(outChannel(0).toInt)
    }
    outChannel.release
    resp
  }

  private def sendValue(value: BigInt, chunk: Int) = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (0 until chunk) foreach (i => inChannel(i) = (value >> (64*i)).toLong)
      inChannel.produce
    }
    inChannel.release
    ready
  }

  private def recvValue(chunk: Int) = {
    outChannel.aquire
    val valid = outChannel.valid
    val value = if (!valid) None else {
      outChannel.consume
      Some(((0 until chunk) foldLeft BigInt(0))(
        (res, i) => res | (int(outChannel(i)) << (64*i))))
    }
    outChannel.release
    value
  }

  private def recvOutputs = {
    _peekMap.clear
    outChannel.aquire
    val valid = outChannel.valid
    if (valid) {
      (_outputs.toList foldLeft 0){case (off, (out, chunk)) =>
        _peekMap(out) = ((0 until chunk) foldLeft BigInt(0))(
          (res, i) => res | (int(outChannel(off + i)) << (64 * i))
        )
        off + chunk
      }
      outChannel.consume
    }
    outChannel.release
    valid
  }

  private def sendInputs = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (_inputs.toList foldLeft 0){case (off, (in, chunk)) =>
        val value = _pokeMap getOrElse (in, BigInt(0))
        (0 until chunk) foreach (i => inChannel(off + i) = (value >> (64 * i)).toLong)
        off + chunk
      }
      inChannel.produce
    }
    inChannel.release
    ready
  }

  private var isStale = false

  private def update {
    mwhile(!sendCmd(SIM_CMD.UPDATE)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }

  private def takeStep {
    mwhile(!sendCmd(SIM_CMD.STEP)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }

  private def getId(path: String) = {
    mwhile(!sendCmd(SIM_CMD.GETID)) { }
    mwhile(!sendCmd(path)) { }
    if (exitValue.isCompleted) {
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }

  private def getChunk(id: Int) = {
    mwhile(!sendCmd(SIM_CMD.GETCHK)) { }
    mwhile(!sendCmd(id)) { }
    if (exitValue.isCompleted){
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }

  private def poke(id: Int, chunk: Int, v: BigInt, force: Boolean = false) {
    val cmd = if (!force) SIM_CMD.POKE else SIM_CMD.FORCE
    mwhile(!sendCmd(cmd)) { }
    mwhile(!sendCmd(id)) { }
    mwhile(!sendValue(v, chunk)) { }
  }

  private def peek(id: Int, chunk: Int) = {
    mwhile(!sendCmd(SIM_CMD.PEEK)) { }
    mwhile(!sendCmd(id)) { }
    if (exitValue.isCompleted) {
      BigInt(0)
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvValue(chunk)
        if data != None
      } yield data.get).head
    }
  }

  def reset(n: Int = 1) {
    for (i <- 0 until n) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }

  private def start {
    println(s"SEED ${_seed}")
    println(s"STARTING ${cmd}")
    mwhile(!recvOutputs) { }
    // reset(5)
    for (i <- 0 until 5) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }

  /** Complete the simulation and inspect all tests */
  def finish: Boolean = {
    try {
      mwhile(!sendCmd(SIM_CMD.FIN)) { }
      while(!exitValue.isCompleted) { }
    }
    catch {
      // Depending on load and timing, we may get a TestApplicationException
      //  when the test application exits.
      //  Check the exit value.
      //  Anything other than 0 is an error.
      case e: TestApplicationException => if (e.exitVal != 0) fail
    }
    _logs.clear
    inChannel.close
    outChannel.close
    cmdChannel.close
    println(s"""RAN ${simTime} CYCLES ${if (ok) "PASSED" else s"FAILED FIRST AT CYCLE ${failureTime}"}""")
    ok
  }

  //initialize cpp process and memory mapped channels
  val cmd = chiselMain.context.testCmd mkString " "
  private val (process: Process, exitValue: Future[Int], inChannel, outChannel, cmdChannel) = {
    val processBuilder = Process(cmd)
    val processLogger = ProcessLogger(println, _logs += _) // don't log stdout
    val process = processBuilder run processLogger

    // Set up a Future to wait for (and signal) the test process exit.
    val exitValue: Future[Int] = Future {
      blocking {
        process.exitValue
      }
    }
    // Wait for the startup message
    // NOTE: There may be several messages before we see our startup message.
    val simStartupMessageStart = "sim start on "
    while (!_logs.exists(_ startsWith simStartupMessageStart) && !exitValue.isCompleted) { Thread.sleep(100) }
    // Remove the startup message (and any precursors).
    while (!_logs.isEmpty && !_logs.head.startsWith(simStartupMessageStart)) {
      println(_logs.remove(0))
    }
    if (!_logs.isEmpty) println(_logs.remove(0)) else println("<no startup message>")
    while (_logs.size < 3) {
      // If the test application died, throw a run-time error.
      throwExceptionIfDead(exitValue)
      Thread.sleep(100)
    }
    val in_channel_name = _logs.remove(0)
    val out_channel_name = _logs.remove(0)
    val cmd_channel_name = _logs.remove(0)
    val in_channel = new Channel(in_channel_name)
    val out_channel = new Channel(out_channel_name)
    val cmd_channel = new Channel(cmd_channel_name)

    println(s"inChannelName: ${in_channel_name}")
    println(s"outChannelName: ${out_channel_name}")
    println(s"cmdChannelName: ${cmd_channel_name}")

    in_channel.consume
    cmd_channel.consume
    in_channel.release
    out_channel.release
    cmd_channel.release

    (process, exitValue, in_channel, out_channel, cmd_channel)
  }

  // Once everything has been prepared, we can start the communications.
  start

  /********************************/
  /*** Classic Tester Interface ***/
  /********************************/
  /* Simulation Time */
  private var simTime = 0L 
  protected[swtesters] def incTime(n: Int) { simTime += n }
  def t = simTime

  private def dumpNode(data: Data) = _nameMap getOrElse (data, "<no signal name>")

  /** Indicate a failure has occurred.  */
  private var failureTime = -1L
  private var ok = true
  def fail = if (ok) {
    failureTime = simTime
    ok = false
  }

  private val _seed = chiselMain.context.testerSeed
  val rnd = new Random(_seed)

  /** Convert a Boolean to BigInt */
  implicit def int(x: Boolean): BigInt = if (x) 1 else 0
  /** Convert an Int to BigInt */
  implicit def int(x: Int):     BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  /** Convert a Long to BigInt */
  implicit def int(x: Long):    BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  /** Convert Bits to BigInt */
  implicit def int(x: Bits):    BigInt = x.litValue()

  protected def bigIntToStr(x: BigInt, base: Int) = base match {
    case 2  if x < 0 => s"-0b${(-x).toString(base)}"
    case 16 if x < 0 => s"-0x${(-x).toString(base)}"
    case 2  => s"0b${x.toString(base)}"
    case 16 => s"0x${x.toString(base)}"
    case _ => x.toString(base)
  }

  def step(n: Int) {
    if (isTrace) println(s"STEP ${simTime} -> ${simTime+n}")
    (0 until n) foreach (_ => takeStep)
    incTime(n)
  }

  def poke(signal: Bits, value: BigInt) {
    if (_inputs contains signal) {
      _pokeMap(signal) = value
      isStale = true
      if (isTrace) println(s"  POKE ${dumpNode(signal)} <- ${bigIntToStr(value, 16)}")
    }
  }

  def pokeAt[T <: Bits](data: Mem[T], value: BigInt, off: Int) {
    // TODO: empty now...
  }

  private def _peek(signal: Bits) = {
    if (isStale) update
    if (_outputs contains signal) _peekMap get signal
    else if (_inputs contains signal) _pokeMap get signal
    else None
  }

  def peek(signal: Bits) = {
    val result = _peek(signal) getOrElse BigInt(rnd.nextInt)
    if (isTrace) println(s"  PEEK ${dumpNode(signal)} -> ${bigIntToStr(result, 16)}")
    result
  }

  def peekAt[T <: Bits](data: Mem[T], off: Int) = {
    BigInt(rnd.nextInt) // TODO
  }

  def expect (good: Boolean, msg: => String): Boolean = {
    if (isTrace) println(s"""EXPECT ${msg} ${if (good) "PASS" else "FAIL"}""")
    if (!good) fail
    good
  }

  def expect(signal: Bits, expected: BigInt, msg: => String = "") = {
    val got = _peek(signal) getOrElse BigInt(rnd.nextInt)
    val good = got == expected
    if (!good) fail
    if (isTrace) println(s"""${msg}  EXPECT ${dumpNode(signal)} -> ${bigIntToStr(got, 16)} == ${bigIntToStr(expected, 16)} ${if (good) "PASS" else "FAIL"}""")
    good
  }
}
