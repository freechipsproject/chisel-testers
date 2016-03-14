Chisel Testers
==============

This a layer of test harnesses for [Chisel](https://github.com/ucb-bar/chisel3)

Chisel is an open-source hardware construction language developed
at UC Berkeley that supports advanced hardware design using highly
parameterized generators and layered domain-specific hardware languages.

Visit the [community website](http://chisel.eecs.berkeley.edu/) for more
information.

The [Chisel Tutorials](https://github.com/ucb-bar/chisel-tutorials) provide many examples of how to use these harnesses

The Available Harnesses
-----------------------

There are currently two harnesses available.  Both make it easy to construct a circuit, implemented as chisel Module, named
 the device under test (or DUT) by specifying what 
goes into the module's inputs and what is expected to come out of the module's outputs.  The types of IO ports used by the
circuit determines which tester to use.  

#### SteppedHWIOTester

Tests the DUT by poking values into its inputs and testing values of its outputs.  Tests are executed in a fixed
order and at a fixed cycle

#### OrderedDecoupledHWIOTester

Tests a DUT that uses decoupled flow control for its inputs and outputs.  Tests values are applied in order 
mediated by the ready/valid controls.  The implementer does not have to manage this flow control.

How to build a test using the SteppedHWIOTester 
-----------------------------------------------

The SteppedHWIOTester and the OrderedDecoupledHWIOTester . Here we will walk through a couple of examples.
There are more examples here under (src/test/scala/examples) in this project.  The [Chisel Tutorials](http://github.com/ucb-bar/chisel-tutorials) 
have a lot more examples of the use of these testers

### A couple of imports
```scala
import Chisel._
import Chisel.hwiotesters.{SteppedHWIOTester, ChiselFlatSpec}
```

### The device under test
We will start with a simple adder, the complete file is under src/test/scala/examples/Adder.scala, 
it has two input and one outputs, it contains no registers so the output contains 
the sum of the inputs as soon as the inputs are set.
```scala
class Adder(val w: Int) extends Module {
  val io = new Bundle {
    val in0 = UInt(INPUT,  w)
    val in1 = UInt(INPUT,  w)
    val out = UInt(OUTPUT, w)
  }
  io.out := io.in0 + io.in1
}
```

### The Test code
The testing is done by creating a subclass of the SteppedHWIOTester.  We will show the complete test
and then discuss the components individually.
```scala
class AdderTests extends SteppedHWIOTester {
  val device_under_test = Module( new Adder(10) )
  val c = device_under_test
  enable_all_debug = true

  rnd.setSeed(0L)
  for (i <- 0 until 10) {
    val in0 = rnd.nextInt(1 << c.w)
    val in1 = rnd.nextInt(1 << c.w)
    poke(c.io.in0, in0)
    poke(c.io.in1, in1)
    expect(c.io.out, (in0 + in1) & ((1 << c.w) - 1))

    step(1)
  }
}
```
AdderTests extends SteppedHWIOTester, this requires it to define a device_under_test
which will be our Adder Module.  
**IMPORTANT: Although Adder is a subclass of Module, it must still be wrapped in a Module**
**Wrong:** `val device_under_test = new Adder(10)`
**Right:** `val device_under_test = Module( new Adder(10) )`
It is common for developers to want a shorter name for the DUT, typically Chisel developers have
used c (for circuit) so we will follow that convention and use the alias `val c = device_under_test`
The testers are fairly silent by default when running, but by using `enabled_all_debug = true` a managable
amount of additional output will be generated show several different things discussed below.




