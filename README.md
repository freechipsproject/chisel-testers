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

### The Code That Implements the Tests
The testing is done by creating a subclass of the SteppedHWIOTester.  We will show the complete test
and then discuss the components individually.
#### Example
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
#### Setup
AdderTests extends SteppedHWIOTester, this requires it to define a device_under_test
which will be our Adder Module.  
**IMPORTANT: Although Adder is a subclass of Module, it must still be wrapped in a Module**
**Wrong:** `val device_under_test = new Adder(10)`
**Right:** `val device_under_test = Module( new Adder(10) )`
It is common for developers to want a shorter name for the DUT, typically Chisel developers have
used c (for circuit) so we will follow that convention and use the alias `val c = device_under_test`
The testers are fairly silent by default when running, but by using `enabled_all_debug = true` a managable
amount of additional output will be generated show several different things discussed below.

#### Implementing the Tests
Use poke() to set IO input ports on the DUT.  Use Expect to test IO output ports on the DUT.  All pokes and expects
that are between a call to step will be performed in the same cycle.  In Chisel3 based testers here there is currently no peek operation, this requires that any delays
in the DUT be explicitly handled by use of the step call.  See chisel tutorials examples/GCD.scala for an tester instance where the number of steps is computed
to match delays in the DUT.

### How to Launch the tests
There are several ways to launch a tester.  Most Chisel developers will make a call to the tester via a scalatest wrapper.
For the AdderTests above this would probably look like
```
class AdderTester extends ChiselFlatSpec {
  "Adder" should "compile and run without incident" in {
    assertTesterPasses { new AdderTests }
  }
}
```
The chisel tutorials use a different method by calling ```TesterDriver.execute()``` 

### Interpreting the Output
#### Useful information about your tests
If you have used ```enable_all_debug = true``` or ```enable_scala_debug = true``` you will see two tables in the output
```
================================================================================
Device under test: io bundle
  #  Dir  D/V   Used   Name                      Parent
--------------------------------------------------------------------------------
  0    I          y    A
  1    I          y    B
  2    I          y    Cin
  3    O          y    Cout
  4    O          y    Sum
================================================================================
================================================================================
UnitTester state table
  step     A     B   Cin  Cout   Sum
--------------------------------------------------------------------------------
     0     3     4     0     7     0
     1     5     0     0     5     0
     2     0     6     0     6     0
     3     1     6     0     7     0
     4     -     -     -     -     -
================================================================================
```
The first table shows the io ports for the DUT and indicate useful stuff like the direction and whether the port is referenced in your tests.
The second table shows a state table for what values are loaded in each cycle and what values are tested.
#### A bunch of compile stuff
```
verilator --cc AdderUnitTester8627519663317449182.v --assert -Wno-fatal -Wno-WIDTH -Wno-STMTDLY --trace -O2 +define+TOP_TYPE=VAdderUnitTester8627519663317449182 -CFLAGS -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h -Mdir /var/folders/ls/0xl3wjy949b2b9j36yn36v3c0000gn/T/AdderUnitTester4932518275812116679 --exe /var/folders/ls/0xl3wjy949b2b9j36yn36v3c0000gn/T/AdderUnitTester4932518275812116679/top.cpp
g++  -I.  -MMD -I/usr/local/share/verilator/include -I/usr/local/share/verilator/include/vltstd -DVL_PRINTF=printf -DVM_TRACE=1 -DVM_COVERAGE=0 -Wno-char-subscripts -Wno-parentheses-equality -Wno-sign-compare -Wno-uninitialized -Wno-unused-parameter -Wno-unused-variable -fbracket-depth=4096 -Qunused-arguments     -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h   -c -o top.o /var/folders/ls/0xl3wjy949b2b9j36yn36v3c0000gn/T/AdderUnitTester4932518275812116679/top.cpp
g++  -I.  -MMD -I/usr/local/share/verilator/include -I/usr/local/share/verilator/include/vltstd -DVL_PRINTF=printf -DVM_TRACE=1 -DVM_COVERAGE=0 -Wno-char-subscripts -Wno-parentheses-equality -Wno-sign-compare -Wno-uninitialized -Wno-unused-parameter -Wno-unused-variable -fbracket-depth=4096 -Qunused-arguments     -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h   -c -o verilated.o /usr/local/share/verilator/include/verilated.cpp
g++  -I.  -MMD -I/usr/local/share/verilator/include -I/usr/local/share/verilator/include/vltstd -DVL_PRINTF=printf -DVM_TRACE=1 -DVM_COVERAGE=0 -Wno-char-subscripts -Wno-parentheses-equality -Wno-sign-compare -Wno-uninitialized -Wno-unused-parameter -Wno-unused-variable -fbracket-depth=4096 -Qunused-arguments     -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h   -c -o verilated_vcd_c.o /usr/local/share/verilator/include/verilated_vcd_c.cpp
/usr/bin/perl /usr/local/share/verilator/bin/verilator_includer -DVL_INCLUDE_OPT=include VAdderUnitTester8627519663317449182.cpp > VAdderUnitTester8627519663317449182__ALLcls.cpp
/usr/bin/perl /usr/local/share/verilator/bin/verilator_includer -DVL_INCLUDE_OPT=include VAdderUnitTester8627519663317449182__Trace.cpp VAdderUnitTester8627519663317449182__Syms.cpp VAdderUnitTester8627519663317449182__Trace__Slow.cpp > VAdderUnitTester8627519663317449182__ALLsup.cpp
g++  -I.  -MMD -I/usr/local/share/verilator/include -I/usr/local/share/verilator/include/vltstd -DVL_PRINTF=printf -DVM_TRACE=1 -DVM_COVERAGE=0 -Wno-char-subscripts -Wno-parentheses-equality -Wno-sign-compare -Wno-uninitialized -Wno-unused-parameter -Wno-unused-variable -fbracket-depth=4096 -Qunused-arguments     -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h   -c -o VAdderUnitTester8627519663317449182__ALLsup.o VAdderUnitTester8627519663317449182__ALLsup.cpp
g++  -I.  -MMD -I/usr/local/share/verilator/include -I/usr/local/share/verilator/include/vltstd -DVL_PRINTF=printf -DVM_TRACE=1 -DVM_COVERAGE=0 -Wno-char-subscripts -Wno-parentheses-equality -Wno-sign-compare -Wno-uninitialized -Wno-unused-parameter -Wno-unused-variable -fbracket-depth=4096 -Qunused-arguments     -Wno-undefined-bool-conversion -O2 -DTOP_TYPE=VAdderUnitTester8627519663317449182 -include VAdderUnitTester8627519663317449182.h   -c -o VAdderUnitTester8627519663317449182__ALLcls.o VAdderUnitTester8627519663317449182__ALLcls.cpp
      Archiving VAdderUnitTester8627519663317449182__ALL.a ...
ar r VAdderUnitTester8627519663317449182__ALL.a VAdderUnitTester8627519663317449182__ALLcls.o VAdderUnitTester8627519663317449182__ALLsup.o
ar: creating archive VAdderUnitTester8627519663317449182__ALL.a
ranlib VAdderUnitTester8627519663317449182__ALL.a
g++    top.o verilated.o verilated_vcd_c.o VAdderUnitTester8627519663317449182__ALL.a    -o VAdderUnitTester8627519663317449182 -lm -lstdc++  2>&1 | c++filt
```
This shows how the command line argument and various compiler calls that went into building a test simulation.  One thing to notice is buried in this
output is the temp directory where this run is being built.  At the end of the verilator command line here it is ```/var/folders/ls/0xl3wjy949b2b9j36yn36v3c0000gn/T/AdderUnitTester4932518275812116679/```
There are several useful files in this directory.  **NOTE:** For a really nice command line alias to take you to this directory see the [Tips and Tricks](https://github.com/ucb-bar/chisel3/wiki/tips-and-tricks)
In particular the FIRRTL output file of the form *.fir is here.  A number of errors that can occur can be found in this
somewhat human readable file.
Another very useful file is the Value Change Dump file dump.vcd that contains the signal showing in great detail how your circuit operated.  Use 
[GTKWave](http://gtkwave.sourceforge.net/) or a similar program to see nice graphical display of this information.
#### The execution ouput
The execution output consists mainly as a lit of confirmed values.  Interfiled with this is some messages from the simulation engine that can be, for the most part,
ignored.
```
    passed step  1 -- out:   866
    passed step  2 -- out:   968
    passed step  3 -- out:   682
Enabling waves...
    passed step  4 -- out:   387
Starting simulation!
    passed step  5 -- out:   599
    passed step  6 -- out:  1021
- /var/folders/ls/0xl3wjy949b2b9j36yn36v3c0000gn/T/AdderTests2343549149345341195/AdderTests4488980559327557212.v:389: Verilog $finish
Simulation completed at time 112 (cycle 11)
    passed step  7 -- out:   990
    passed step  8 -- out:   923
    passed step  9 -- out:   119
Stopping, end of tests, 11 steps
0
```
Unexpected output will result in a rather terse entry like
```
    failed on step  1 -- port out:   866 expected    2
```
Debugging will ensue.  
#### The rest of the output
There are few lines of additional output, 
##### Assuming Failure
```
[info] AdderTester:
[info] Adder
[info] - should compile and run without incident *** FAILED ***
[info]   ChiselRunners.this.runTester(t, additionalVResources) was false (ChiselSpec.scala:17)
[info] ScalaCheck
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] ScalaTest
[info] Run completed in 2 seconds, 418 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0
[info] *** 1 TEST FAILED ***
[error] Failed: Total 1, Failed 1, Errors 0, Passed 0
[error] Failed tests:
[error] 	examples.AdderTester
[error] (test:testOnly) sbt.TestsFailedException: Tests unsuccessful
[error] Total time: 7 s, completed Mar 14, 2016 7:38:27 PM
```
##### Assuming Success
```
[info] AdderTester:
[info] Adder
[info] - should compile and run without incident
[info] ScalaCheck
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] ScalaTest
[info] Run completed in 2 seconds, 572 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[info] Passed: Total 1, Failed 0, Errors 0, Passed 1
[success] Total time: 3 s, completed Mar 14, 2016 7:35:41 PM
```
Aim for success!
