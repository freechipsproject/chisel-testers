package chisel3.iotesters

import chisel3.internal.firrtl.Circuit
import chisel3.stage.CircuitSerializationAnnotation.FirrtlFileFormat
import chisel3.stage.phases.MaybeFirrtlStage
import chisel3.stage.{ChiselCircuitAnnotation, ChiselGeneratorAnnotation, ChiselOutputFileAnnotation, ChiselStage, CircuitSerializationAnnotation, NoRunFirrtlCompilerAnnotation}
import chisel3.{HasChiselExecutionOptions, RawModule}
import firrtl.annotations.NoTargetAnnotation
import firrtl.options.Viewer.view
import firrtl.options.phases.DeletedWrapper
import firrtl.options.{Dependency, OptionsException, OptionsView, OutputAnnotationFileAnnotation, Phase, PhaseManager, StageError, Unserializable}
import firrtl.stage.phases.DriverCompatibility.TopNameAnnotation
import firrtl.stage.phases.DriverCompatibilityExtensions
import firrtl.stage.{FirrtlCircuitAnnotation, RunFirrtlTransformAnnotation}
import firrtl.{AnnotationSeq, ExecutionOptionsManager, FirrtlExecutionResult, HasFirrtlOptions}

/** This provides components of a compatibility wrapper around Chisel's removed `chisel3.Driver`.
  *
  * Primarily, this object includes [[firrtl.options.Phase Phase]]s that generate [[firrtl.annotations.Annotation]]s
  * derived from the deprecated [[firrtl.stage.phases.DriverCompatibility.TopNameAnnotation]].
  */
object DriverCompatibility {

  private[chisel3] implicit object ChiselExecutionResultView extends OptionsView[ChiselExecutionResult] {

    def view(options: AnnotationSeq): ChiselExecutionResult = {
      var chiselCircuit: Option[chisel3.internal.firrtl.Circuit] = None
      var chirrtlCircuit: Option[String] = None

      options.foreach {
        case a@ChiselCircuitAnnotation(b) =>
          chiselCircuit = Some(b)
          chirrtlCircuit = {
            val anno = CircuitSerializationAnnotation(a.circuit, "", FirrtlFileFormat)
            Some(anno.getBytes.map(_.toChar).mkString)
          }
        case _ =>
      }

      val fResult = firrtl.stage.phases.DriverCompatibility.firrtlResultView(options)

      (chiselCircuit, chirrtlCircuit) match {
        case (None, _) => ChiselExecutionFailure("Failed to elaborate Chisel circuit")
        case (Some(_), None) => ChiselExecutionFailure("Failed to convert Chisel circuit to FIRRTL")
        case (Some(a), Some(b)) => ChiselExecutionSuccess(Some(a), b, Some(fResult))
      }

    }

  }

  def execute(optionsManager: ExecutionOptionsManager with HasChiselExecutionOptions with HasFirrtlOptions,
              dut: () => RawModule): ChiselExecutionResult = {

    val annos: AnnotationSeq =
      Seq(DriverCompatibility.OptionsManagerAnnotation(optionsManager), ChiselGeneratorAnnotation(dut)) ++
        optionsManager.chiselOptions.toAnnotations ++
        optionsManager.firrtlOptions.toAnnotations ++
        optionsManager.commonOptions.toAnnotations

    val targets =
      Seq(Dependency[DriverCompatibility.AddImplicitOutputFile],
        Dependency[DriverCompatibility.AddImplicitOutputAnnotationFile],
        Dependency[DriverCompatibility.DisableFirrtlStage],
        Dependency[ChiselStage],
        Dependency[DriverCompatibility.MutateOptionsManager],
        Dependency[DriverCompatibility.ReEnableFirrtlStage],
        Dependency[DriverCompatibility.FirrtlPreprocessing],
        Dependency[chisel3.stage.phases.MaybeFirrtlStage])
    val currentState =
      Seq(Dependency[firrtl.stage.phases.DriverCompatibility.AddImplicitFirrtlFile],
        Dependency[chisel3.stage.phases.Convert])

    val phases: Seq[Phase] = new PhaseManager(targets, currentState) {
      override val wrappers = Seq(DeletedWrapper(_: Phase))
    }.transformOrder

    val annosx = try {
      phases.foldLeft(annos)((a, p) => p.transform(a))
    } catch {
      /* ChiselStage and FirrtlStage can throw StageError. Since Driver is not a StageMain, it cannot catch these. While
       * Driver is deprecated and removed in 3.2.1+, the Driver catches all errors.
       */
      case e: StageError => annos
    }

    view[ChiselExecutionResult](annosx)
  }

  /**
    * This family provides return values from the chisel3 and possibly firrtl compile steps
    */
  trait ChiselExecutionResult

  /**
    *
    * @param circuitOption      Optional circuit, has information like circuit name
    * @param emitted            The emitted Chirrrl text
    * @param firrtlResultOption Optional Firrtl result, @see freechipsproject/firrtl for details
    */
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
  case class ChiselExecutionFailure(message: String) extends ChiselExecutionResult


  /** Adds a [[ChiselOutputFileAnnotation]] derived from a [[TopNameAnnotation]] if no [[ChiselOutputFileAnnotation]]
    * already exists. If no [[TopNameAnnotation]] exists, then no [[firrtl.stage.OutputFileAnnotation]] is added. ''This is not a
    * replacement for [[chisel3.stage.phases.AddImplicitOutputFile AddImplicitOutputFile]] as this only adds an output
    * file based on a discovered top name and not on a discovered elaborated circuit.'' Consequently, this will provide
    * the correct behavior before a circuit has been elaborated.
    *
    * @note the output suffix is unspecified and will be set by the underlying [[firrtl.EmittedComponent]]
    */
  private[iotesters] class AddImplicitOutputFile extends Phase {

    override def prerequisites = Seq.empty

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq(Dependency[chisel3.stage.ChiselStage])

    override def invalidates(a: Phase) = false

    def transform(annotations: AnnotationSeq): AnnotationSeq = {
      val hasOutputFile = annotations
        .collectFirst { case a: ChiselOutputFileAnnotation => a }
        .isDefined
      lazy val top = annotations.collectFirst { case TopNameAnnotation(a) => a }

      if (!hasOutputFile && top.isDefined) {
        ChiselOutputFileAnnotation(top.get) +: annotations
      } else {
        annotations
      }
    }
  }

  /** If a [[firrtl.options.OutputAnnotationFileAnnotation]] does not exist, this adds one derived from a
    * [[TopNameAnnotation]]. ''This is not a replacement for [[chisel3.stage.phases.AddImplicitOutputAnnotationFile]] as
    * this only adds an output annotation file based on a discovered top name.'' Consequently, this will provide the
    * correct behavior before a circuit has been elaborated.
    *
    * @note the output suffix is unspecified and will be set by [[firrtl.options.phases.WriteOutputAnnotations]]
    */
  private[iotesters] class AddImplicitOutputAnnotationFile extends Phase {

    override def prerequisites = Seq.empty

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq(Dependency[chisel3.stage.ChiselStage])

    override def invalidates(a: Phase) = false

    def transform(annotations: AnnotationSeq): AnnotationSeq =
      annotations
        .collectFirst { case _: OutputAnnotationFileAnnotation => annotations }
        .getOrElse {
          val top = annotations.collectFirst { case TopNameAnnotation(a) => a }
          if (top.isDefined) {
            OutputAnnotationFileAnnotation(top.get) +: annotations
          } else {
            annotations
          }
        }
  }

  private[iotesters] case object RunFirrtlCompilerAnnotation extends NoTargetAnnotation

  /** Disables the execution of [[firrtl.stage.FirrtlStage]]. This can be used to call [[chisel3.stage.ChiselStage]] and
    * guarantee that the FIRRTL compiler will not run. This is necessary for certain `chisel3.Driver` compatibility
    * situations where you need to do something between Chisel compilation and FIRRTL compilations, e.g., update a
    * mutable data structure.
    */
  private[iotesters] class DisableFirrtlStage extends Phase {

    override def prerequisites = Seq.empty

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq(Dependency[ChiselStage])

    override def invalidates(a: Phase) = false

    def transform(annotations: AnnotationSeq): AnnotationSeq = annotations
      .collectFirst { case NoRunFirrtlCompilerAnnotation => annotations }
      .getOrElse {
        Seq(RunFirrtlCompilerAnnotation, NoRunFirrtlCompilerAnnotation) ++ annotations
      }
  }

  private[iotesters] class ReEnableFirrtlStage extends Phase {

    override def prerequisites = Seq(Dependency[DisableFirrtlStage], Dependency[ChiselStage])

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq.empty

    override def invalidates(a: Phase) = false

    def transform(annotations: AnnotationSeq): AnnotationSeq = annotations
      .collectFirst { case RunFirrtlCompilerAnnotation =>
        val a: AnnotationSeq = annotations.filter {
          case NoRunFirrtlCompilerAnnotation | RunFirrtlCompilerAnnotation => false
          case _ => true
        }
        a
      }
      .getOrElse {
        annotations
      }

  }

  private[iotesters] case class OptionsManagerAnnotation(
                                                          manager: ExecutionOptionsManager with HasChiselExecutionOptions with HasFirrtlOptions)
    extends NoTargetAnnotation with Unserializable

  /** Mutate an input [[firrtl.ExecutionOptionsManager]] based on information encoded in an [[firrtl.AnnotationSeq]].
    * This is intended to be run between [[chisel3.stage.ChiselStage ChiselStage]] and [[firrtl.stage.FirrtlStage]] if
    * you want to have backwards compatibility with an [[firrtl.ExecutionOptionsManager]].
    */
  private[iotesters] class MutateOptionsManager extends Phase {

    override def prerequisites = Seq(Dependency[chisel3.stage.ChiselStage])

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq(Dependency[ReEnableFirrtlStage])

    override def invalidates(a: Phase) = false

    def transform(annotations: AnnotationSeq): AnnotationSeq = {

      val optionsManager = annotations
        .collectFirst { case OptionsManagerAnnotation(a) => a }
        .getOrElse {
          throw new OptionsException(
            "An OptionsManagerException must exist for Chisel Driver compatibility mode")
        }

      val firrtlCircuit = annotations.collectFirst { case FirrtlCircuitAnnotation(a) => a }
      optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(
        firrtlCircuit = firrtlCircuit,
        annotations = optionsManager.firrtlOptions.annotations ++ annotations,
        customTransforms = optionsManager.firrtlOptions.customTransforms ++
          annotations.collect { case RunFirrtlTransformAnnotation(a) => a })

      annotations

    }

  }

  /** A [[Phase]] that lets us run
    *
    * @todo a better solution than the current state hack below may be needed
    */
  private[iotesters] class FirrtlPreprocessing extends Phase {

    override def prerequisites = Seq(Dependency[ChiselStage], Dependency[MutateOptionsManager], Dependency[ReEnableFirrtlStage])

    override def optionalPrerequisites = Seq.empty

    override def optionalPrerequisiteOf = Seq(Dependency[MaybeFirrtlStage])

    override def invalidates(a: Phase) = false

    private val phases =
      Seq(new firrtl.stage.phases.DriverCompatibility.AddImplicitOutputFile,
        new firrtl.stage.phases.DriverCompatibility.AddImplicitEmitter)

    override def transform(annotations: AnnotationSeq): AnnotationSeq =
      phases
        .foldLeft(annotations)((a, p) => p.transform(a))

  }

}
