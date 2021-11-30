// SPDX-License-Identifier: Apache-2.0

package chisel3

import chisel3.internal.ErrorLog
import internal.firrtl._
import firrtl._
import firrtl.options.{Dependency, Phase, PhaseManager, StageError}
import firrtl.options.phases.DeletedWrapper
import firrtl.options.Viewer.view
import firrtl.annotations.JsonProtocol
import firrtl.util.{BackendCompilationUtilities => FirrtlBackendCompilationUtilities}
import chisel3.stage.{ChiselExecutionResultView, ChiselGeneratorAnnotation, ChiselStage}
import chisel3.stage.phases.DriverCompatibility
import java.io._


/**
  * This family provides return values from the chisel3 and possibly firrtl compile steps
  */
@deprecated("This will be removed in Chisel 3.5", "Chisel3 3.4")
trait ChiselExecutionResult

/**
  *
  * @param circuitOption  Optional circuit, has information like circuit name
  * @param emitted            The emitted Chirrrl text
  * @param firrtlResultOption Optional Firrtl result, @see freechipsproject/firrtl for details
  */
@deprecated("This will be removed in Chisel 3.5", "Chisel 3.4")
case class ChiselExecutionSuccess(
                                  circuitOption: Option[Circuit],
                                  emitted: String,
                                  firrtlResultOption: Option[FirrtlExecutionResult]
                                  ) extends ChiselExecutionResult

/**
  * Getting one of these indicates failure of some sort.
  *
  * @param message A clue might be provided here.
  */
@deprecated("This will be removed in Chisel 3.5", "Chisel 3.4")
case class ChiselExecutionFailure(message: String) extends ChiselExecutionResult
