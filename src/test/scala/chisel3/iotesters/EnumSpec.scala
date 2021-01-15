// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._
import org.scalatest.matchers.should.Matchers

object MyEnum extends ChiselEnum {
  val e0, e1, e3, e4 = Value
}

// Passes an enum with one cycle delay
class EnumPassThrough extends Module {
  val io = IO(new Bundle {
    val in = Input(MyEnum())
    val out = Output(MyEnum())
  })

  io.out := RegNext(io.in)
}

// Passes a Vec of enums with one cycle delay
class EnumVecPassThrough(size: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(size, MyEnum()))
    val out = Output(Vec(size, MyEnum()))
  })

  io.out <> RegNext(io.in)
}

class EnumMem(val size: Int) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(log2Ceil(size).W))
    val read = Output(MyEnum())

    val read_u = Output(UInt(32.W))
    val read_s = Output(SInt(32.W))
  })

  val mem = Mem(size, MyEnum())
  io.read := mem(io.addr)

  val mem_u = Mem(size, UInt(32.W))
  val mem_s = Mem(size, SInt(32.W))

  io.read_u := mem_u(io.addr)
  io.read_s := mem_s(io.addr)
}

class EnumPeekPokeTester(c: EnumPassThrough) extends PeekPokeTester(c) {
  for (e <- MyEnum.all) {
    poke(c.io.in, e)
    step(1)
    expect(c.io.out, e)
  }
}

class IncorrectEnumPeekPokeTester(c: EnumPassThrough) extends PeekPokeTester(c) {
  for (e <- MyEnum.all) {
    poke(c.io.in, e)
    step(1)
    expect(c.io.out, MyEnum.all.head)
  }
}

class EnumVecPeekPokeTester(c: EnumVecPassThrough) extends PeekPokeTester(c) {
  // When poking Vecs directly, enums must be converted to their literal values. This is because there is currently no
  // implicit conversion between IndexedSeq[EnumType] and IndexedSeq[BigInt].

  poke(c.io.in, MyEnum.all.toIndexedSeq.map(_.litValue()))
  step(1)
  expect(c.io.out, MyEnum.all.toIndexedSeq.map(_.litValue()))
}

class EnumMemPeekPokeTester(c: EnumMem) extends PeekPokeTester(c) {
  for (i <- 0 until c.size) {
    val e = MyEnum.all(i % MyEnum.all.size)
    pokeAt(c.mem, e, i)
    expect(peekAt(c.mem, i) == e.litValue, "Enum memory is not correct")
  }

  for (i <- 0 until c.size) {
    val e = MyEnum.all(i % MyEnum.all.size)
    poke(c.io.addr, i)
    step(1)
    expect(c.io.read, e, "Enum memory is incorrect")
  }
}

class ReadyValidEnumShifter(val delay: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(DecoupledIO(MyEnum()))
    val out = ValidIO(MyEnum())
  })

  val cnt = RegInit(0.U(log2Ceil(delay+1).W))
  val req_fire = io.in.ready && io.in.valid

  cnt := 0.U
  when (req_fire || (cnt > 0.U && cnt < delay.U)) {
    cnt := cnt + 1.U
  }

  io.out.bits := ShiftRegister(io.in.bits, delay)
  io.out.valid := cnt >= delay.U
  io.in.ready := cnt === 0.U
}

class EnumAdvTester(c: ReadyValidEnumShifter) extends AdvTester(c)  {
  val enumShiftOutputHandler = new ValidSink(c.io.out, (outPort: MyEnum.Type) => {
    peek(outPort)
  })

  val enumShiftInputDriver = new DecoupledSource(c.io.in, (inPort: MyEnum.Type, inValue: MyEnum.Type) => {
    wire_poke(inPort, inValue)
  })

  for (e <- MyEnum.all) {
    enumShiftInputDriver.inputs.enqueue(e)
    enumShiftInputDriver.process()
    eventually(enumShiftOutputHandler.outputs.nonEmpty, c.delay + 10)
    val result = enumShiftOutputHandler.outputs.dequeue()
    println(s"Result = $result")
    assert(result == e.litValue(), "Enum output was not correct")
  }
}

class EnumSpec extends ChiselFlatSpec with Matchers {
  def testPeekPoke(args: Array[String], skip_mem: Boolean = false) = {
    iotesters.Driver.execute(args, () => new EnumPassThrough) { c =>
      new EnumPeekPokeTester(c)
    } &&
    !iotesters.Driver.execute(args, () => new EnumPassThrough) { c =>
      new IncorrectEnumPeekPokeTester(c)
    } &&
    iotesters.Driver.execute(args, () => new EnumVecPassThrough(256)) { c =>
      new EnumVecPeekPokeTester(c)
    } &&
    (skip_mem || iotesters.Driver.execute(args, () => new EnumMem(256)) { c =>
      new EnumMemPeekPokeTester(c)
    })
  }

  behavior of "Enum PeekPokeTesters"

  it should "work with a firrtl backend" in {
    testPeekPoke(Array("--backend-name", "firrtl")) should be(true)
  }

  it should "work with a treadle backend" in {
    testPeekPoke(Array("--backend-name", "treadle")) should be(true)
  }

  // pokeAt and peekAt seem to be broken when using Verilator, so we skip the memory tests
  it should "work with a verilator backend" in {
    testPeekPoke(Array("--backend-name", "verilator"), true) should be(true)
  }

  behavior of "Enum AdvTester"

  it should "work with a firrtl backend" in {
    iotesters.Driver.execute(Array("--backend-name", "firrtl"), () => new ReadyValidEnumShifter(4)) { c =>
      new EnumAdvTester(c)
    } should be(true)
  }

  it should "work with a treadle backend" in {
    iotesters.Driver.execute(Array("--backend-name", "treadle"), () => new ReadyValidEnumShifter(4)) { c =>
      new EnumAdvTester(c)
    } should be(true)
  }

  it should "work with a verilator backend" in {
    iotesters.Driver.execute(Array("--backend-name", "verilator"), () => new ReadyValidEnumShifter(4)) { c =>
      new EnumAdvTester(c)
    } should be(true)
  }
}
