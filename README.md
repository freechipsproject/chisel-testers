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

There are currently two harnesses available.  Both create a circuit that implements the desired tests.  This allows
the tests to be run at full simulation speed.

####SteppedHWIOTester

Tests a circuit by poking values into its inputs and testing values of its outputs.  Tests are executed in a fixed
order and at a fixed cycle

####OrderedDecoupledHWIOTester

Tests a circuit that uses decoupled flow control for its inputs and outputs.  Tests values are applied in order 
mediated by the ready/valid controls.  The implementer does not have to manage this flow control.
