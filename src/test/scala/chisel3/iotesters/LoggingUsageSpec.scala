/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package chisel3.iotesters

import chisel3._
import logger.{LazyLogging, Logger}
import org.scalatest.{FreeSpec, Matchers}

class DutWithLogging extends Module with LazyLogging {
  val io = IO(new Bundle {})

  logger.error("error level message")
  logger.warn("warn level message")
  logger.info("info level message")
  logger.debug("debug level message")
  logger.trace("trace level message")
}

class DutWithLoggingTester(c: DutWithLogging) extends PeekPokeTester(c)

class LoggingUsageSpec extends FreeSpec with Matchers {
  "logging can be emitted during hardware generation" - {
    "level defaults to error" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        iotesters.Driver.execute(Array.empty[String], () => new DutWithLogging) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include("warn level message")
        captor.getOutputAsString should not include "info level message"
        captor.getOutputAsString should not include "debug level message"
        captor.getOutputAsString should not include "trace level message"
      }
    }
    "logging level can be set via command line args" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        iotesters.Driver.execute(Array("--log-level", "info"), () => new DutWithLogging) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include ("warn level message")
        captor.getOutputAsString should include ("info level message")
        captor.getOutputAsString should not include "debug level message"
        captor.getOutputAsString should not include "trace level message"
      }
    }
    "logging level can be set for a specific package" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        iotesters.Driver.execute(Array("--class-log-level", "chisel3.iotesters:warn"), () => new DutWithLogging) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include ("warn level message")
        captor.getOutputAsString should not include "info level message"
        captor.getOutputAsString should not include "debug level message"
        captor.getOutputAsString should not include "trace level message"
      }
    }
  }
}
