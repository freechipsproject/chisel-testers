TEST!

Chisel Testers
==============

This a layer of test harnesses for [Chisel](https://github.com/ucb-bar/chisel3)

Chisel is an open-source hardware construction language developed
at UC Berkeley that supports advanced hardware design using highly
parameterized generators and layered domain-specific hardware languages.

Visit the [community website](http://chisel.eecs.berkeley.edu/) for more
information.

The [Chisel Tutorials](https://github.com/ucb-bar/chisel-tutorial) provide many examples of how to use these harnesses

The Available Harnesses
-----------------------

There are currently three harnesses available.  All make it easy to construct a circuit, implemented as chisel Module, named
 the device under test (or DUT) by specifying what 
goes into the module's inputs and what is expected to come out of the module's outputs.  The types of IO ports used by the
circuit determines which tester to use.  

#### PeekPokeTester

Tests the DUT by poking values into its inputs and testing values of its outputs.  PeekPokeTester is the most flexible 
tester, peeks and pokes are done in a software based model.  Peeking can be done at any time, and the value returned can
be tested and used to take different branches during the text execution.  The PeekPokeTester supports two separate backends.
 1. The [Firrtl Interpreter](https://github.com/ucb-bar/firrtl-interpreter): a lightweight scala based low firrtl execution engine, with rapid spinup but slower overall speed.
 1. A verilator backend: which builds a c++ compiled circuit emulation.  Faster execution, but with a longer spinup.

This is in contrast to the following hardware oriented testers, in which a testing circuit is built that drives the
circuit, or device under test (DUT) from value vectors for each input.  Testing the outputs each cycle against a separate
set of value vectors for each output.

For a longer descripton see the [Using the PeekPokeTester](https://github.com/ucb-bar/chisel-testers/wiki/Using%20the%20PeekPokeTester) see the 

#### SteppedHWIOTester

Tests the DUT by poking values into its inputs and testing values of its outputs.  Tests are executed in a fixed
order and at a fixed cycle

For a long description see the [Using the Hardware IO Testers](https://github.com/ucb-bar/chisel-testers/wiki/Using-the-Hardware-IO-Testers)

#### OrderedDecoupledHWIOTester

Tests a DUT that uses decoupled flow control for its inputs and outputs.  Tests values are applied in order 
mediated by the ready/valid controls.  The implementer does not have to manage this flow control.

