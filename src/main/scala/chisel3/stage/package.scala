// SPDX-License-Identifier: Apache-2.0

package chisel3

import firrtl._
import firrtl.options.OptionsView

import chisel3.internal.firrtl.{Circuit => ChiselCircuit}
import chisel3.stage.CircuitSerializationAnnotation.FirrtlFileFormat

package object stage {

  private[chisel3] implicit object ChiselExecutionResultView extends OptionsView[ChiselExecutionResult] {

    def view(options: AnnotationSeq): ChiselExecutionResult = {
      var chiselCircuit: Option[ChiselCircuit] = None
      var chirrtlCircuit: Option[String] = None

      options.foreach {
        case a @ ChiselCircuitAnnotation(b) =>
          chiselCircuit = Some(b)
          chirrtlCircuit = {
            val anno = CircuitSerializationAnnotation(a.circuit, "", FirrtlFileFormat)
            Some(anno.getBytes.map(_.toChar).mkString)
          }
        case _ =>
      }

      val fResult = firrtl.stage.phases.DriverCompatibility.firrtlResultView(options)

      (chiselCircuit, chirrtlCircuit) match {
        case (None, _)          => ChiselExecutionFailure("Failed to elaborate Chisel circuit")
        case (Some(_), None)    => ChiselExecutionFailure("Failed to convert Chisel circuit to FIRRTL")
        case (Some(a), Some(b)) => ChiselExecutionSuccess( Some(a), b, Some(fResult))
      }

    }

  }

}
