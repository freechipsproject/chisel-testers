// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3.testers.TesterDriver

object Solutions {
  def main(args: Array[String]) {
//    TesterDriver.execute( { () => new DecoupledRealGCDTester })
    TesterDriver.execute( { () => new RealGCDTests })
//    TesterDriver.execute( { () => new AdderTests })
  }
}
