// See LICENSE for license details.

package chisel3.iotesters

import firrtl.FirrtlExecutionOptions
import firrtl_interpreter.InterpreterExecutionOptions
import logger.LogLevel
import treadle.TreadleExecutionOptions

class OptionsCompatibility {

}


class TesterOptionsManager {
  var testerOptions = TesterOptions()
  var interpreterOptions = InterpreterExecutionOptions()
  var firrtlOptions      = FirrtlExecutionOptions()
  var commonOptions      = CommonOptions()
  var treadleOptions     = TreadleExecutionOptions()

  def targetDirName: String = firrtlOptions.targetDirName

  def parse(args: Array[String]): Boolean = {
    true
  }
}

case class CommonOptions(
  topName:           String         = "",
  targetDirName:     String         = ".",
  globalLogLevel:    LogLevel.Value = LogLevel.None,
  logToFile:         Boolean        = false,
  logClassNames:     Boolean        = false,
  classLogLevels: Map[String, LogLevel.Value] = Map.empty,
  programArgs:    Seq[String]                 = Seq.empty
)




