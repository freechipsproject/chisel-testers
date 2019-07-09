// See LICENSE for license details.
package chisel3.iotesters

import java.io._
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{FileAlreadyExistsException, Files, Paths}

import chisel3._
import chisel3.experimental.{FixedPoint, MultiIOModule}
import chisel3.internal.InstanceId
import firrtl._
import firrtl.annotations.CircuitName
import firrtl.transforms._

import scala.sys.process.ProcessBuilder

/**
  * Copies the necessary header files used for verilator compilation to the specified destination folder
  */
object copyVerilatorHeaderFiles {
  def apply(destinationDirPath: String): Unit = {
    new File(destinationDirPath).mkdirs()
    val simApiHFilePath = Paths.get(destinationDirPath + "/sim_api.h")
    val verilatorApiHFilePath = Paths.get(destinationDirPath + "/veri_api.h")
    try {
      Files.createFile(simApiHFilePath)
      Files.createFile(verilatorApiHFilePath)
    } catch {
      case _: FileAlreadyExistsException =>
        System.out.format("")
      case x: IOException =>
        System.err.format("createFile error: %s%n", x)
    }

    Files.copy(getClass.getResourceAsStream("/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(getClass.getResourceAsStream("/veri_api.h"), verilatorApiHFilePath, REPLACE_EXISTING)
  }
}
/**
  * Generates the Module specific verilator harness cpp file for verilator compilation
  */
object VerilatorCppHarnessGenerator {
  def codeGen(dut: MultiIOModule, state: CircuitState, vcdFilePath: String): String = {
    val codeBuffer = new StringBuilder

    def pushBack(vector: String, pathName: String, width: BigInt) {
      if (width == 0) {
        // Do nothing- 0 width wires are removed
      } else if (width <= 8) {
        codeBuffer.append(s"        s->sim_data.$vector.push_back(new VerilatorCData(&($pathName)));\n")
      } else if (width <= 16) {
        codeBuffer.append(s"        s->sim_data.$vector.push_back(new VerilatorSData(&($pathName)));\n")
      } else if (width <= 32) {
        codeBuffer.append(s"        s->sim_data.$vector.push_back(new VerilatorIData(&($pathName)));\n")
      } else if (width <= 64) {
        codeBuffer.append(s"        s->sim_data.$vector.push_back(new VerilatorQData(&($pathName)));\n")
      } else {
        val numWords = (width-1)/32 + 1
        codeBuffer.append(s"        s->sim_data.$vector.push_back(new VerilatorWData($pathName, $numWords));\n")
      }
    }

    val (inputs, outputs) = getPorts(dut, "->")
    val dutName = dut.name
    val dutApiClassName = dutName + "_api_t"
    val dutVerilatorClassName = "V" + dutName
    codeBuffer.append(s"""
#include "${dutVerilatorClassName}.h"
#include "verilated.h"
#include "veri_api.h"
#if VM_TRACE
#include "verilated_vcd_c.h"
#endif
#include <iostream>

// Override Verilator definition so first $$finish ends simulation
// Note: VL_USER_FINISH needs to be defined when compiling Verilator code
void vl_finish(const char* filename, int linenum, const char* hier) {
  Verilated::flushCall();
  exit(0);
}

#ifdef INCLUDE_MAIN
#else /* INCLUDE_MAIN */
#include <jni.h>

struct sim_state {
  $dutVerilatorClassName* dut;
  VerilatedVcdC* tfp;
  vluint64_t main_time;
  sim_data_t<VerilatorDataWrapper*> sim_data;

  sim_state() :
    dut(new $dutVerilatorClassName),
    tfp(new VerilatedVcdC),
    main_time(0)
  {
    std::cout << "Allocating! " << ((long long) dut) << std::endl;
  }

};


extern "C" {

jfieldID getPtrId(JNIEnv *env, jobject obj) {
  jclass c = env->GetObjectClass(obj);
  jfieldID id = env->GetFieldID(c, "state", "J");
  env->DeleteLocalRef(c);

  return id;
}

sim_state* get_state(JNIEnv *env, jobject obj) {
  static sim_state* cached = NULL;
  if (cached == NULL) {
    cached = (sim_state*) env->GetLongField(obj, getPtrId(env, obj));
  }
  return cached;
}

JNIEXPORT void JNICALL Java_chisel3_iotesters_TesterSharedLib_sim_1init(JNIEnv *env, jobject obj) {
  sim_state *s = new sim_state();

  env->SetLongField(obj, getPtrId(env, obj), (jlong)s);

    // Verilated::commandArgs(argc, argv);
    // s->dut = new $dutVerilatorClassName;
    std::string vcdfile = "${vcdFilePath}";
    // std::vector<std::string> args(argv+1, argv+argc);
    // std::vector<std::string>::const_iterator it;
    // for (it = args.begin() ; it != args.end() ; it++) {
    //     if (it->find("+waveform=") == 0) vcdfile = it->c_str()+10;
    // }
#if VM_TRACE
    Verilated::traceEverOn(true);
    VL_PRINTF(\"Enabling waves..\");
    // s->tfp = new VerilatedVcdC;
    // s->main_time = 0;
    s->dut->trace(s->tfp, 99);
    s->tfp->open(vcdfile.c_str());
#endif
  s->sim_data.inputs.clear();
  s->sim_data.outputs.clear();
  s->sim_data.signals.clear();

""")
    var signalMapCnt = 0
  inputs.toList foreach { case (node, name) =>
    // TODO this won't work if circuit name has underscore in it
    val mapName = node.pathName.replace(".", "_").replaceFirst("_", ".")
    // replaceFirst used here in case port name contains the dutName
    pushBack("signals", name replaceFirst (dutName, "s->dut"), node.getWidth)
    codeBuffer.append(s"""        s->sim_data.signal_map["$mapName"] = $signalMapCnt;""")
    signalMapCnt += 1
  }
  outputs.toList foreach { case (node, name) =>
    val mapName = node.pathName.replace(".", "_").replaceFirst("_", ".")
    // replaceFirst used here in case port name contains the dutName
    pushBack("signals", name replaceFirst (dutName, "s->dut"), node.getWidth)
    codeBuffer.append(s"""        s->sim_data.signal_map["$mapName"] = $signalMapCnt;""")
    signalMapCnt += 1
  }
  pushBack("signals", "s->dut->reset", 1)
  codeBuffer.append(s"""        s->sim_data.signal_map["${dut.reset.pathName}"] = $signalMapCnt;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_step(JNIEnv *env, jobject obj) {
  sim_state *s = get_state(env, obj);

  // std::cout << "Stepping" << std::endl;
  s->dut->clock = 0;
  s->dut->eval();
#if VM_TRACE
  if (s->tfp) s->tfp->dump(s->main_time);
#endif /* VM_TRACE */
  s->dut->clock = 1;
  s->dut->eval();
#if VM_TRACE
  if (s->tfp) s->tfp->dump(s->main_time);
#endif /* VM_TRACE */
  s->main_time++;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_reset(JNIEnv *env, jobject obj) {
  sim_state *s = get_state(env, obj);

  s->dut->reset = 1;
  Java_chisel3_iotesters_TesterSharedLib_step(env, obj);
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_update(JNIEnv *env, jobject obj) {
  sim_state *s = get_state(env, obj);

  s->dut->_eval_settle(s->dut->__VlSymsp);
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_start(JNIEnv *env, jobject obj) {
  sim_state *s = get_state(env, obj);

  s->dut->reset = 0;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_finish(JNIEnv *env, jobject obj) {
  sim_state *s = get_state(env, obj);

  s->dut->eval();
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_poke(JNIEnv *env, jobject obj, jint id, jint value) {
  sim_state *s = get_state(env, obj);

  VerilatorDataWrapper *sig = s->sim_data.signals[id];
  if (!sig) {
    std::cerr << "Cannot find the object of id = " << id << std::endl;
    Java_chisel3_iotesters_TesterSharedLib_finish(env, obj);
    // TODO what?
  } else {
    // std::cout << "Poking signal " << id << " with value " << value << std::endl;
  }
  uint64_t toput = value;
  sig->put_value(&toput);
}

JNIEXPORT jint Java_chisel3_iotesters_TesterSharedLib_peek(JNIEnv *env, jobject obj, jint id) {
  sim_state *s = get_state(env, obj);

  VerilatorDataWrapper *sig = s->sim_data.signals[id];
  if (!sig) {
    std::cerr << "Cannot find the object of id = " << id << std::endl;
    Java_chisel3_iotesters_TesterSharedLib_finish(env, obj);
    // TODO what?
  } else {
    // std::cout << "Peeking signal " << id << std::endl;
  }
  uint64_t toret;
  sig->get_value(&toret);
  return toret;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_force(JNIEnv *env, jobject obj) {
}

JNIEXPORT jint Java_chisel3_iotesters_TesterSharedLib_getid(JNIEnv *env, jobject obj, jstring jniPath) {
  sim_state *s = get_state(env, obj);

  const char *path = env->GetStringUTFChars(jniPath, NULL);

  std::map<std::string, size_t>::iterator it;

  it = s->sim_data.signal_map.find(path);
  jint id = -1;

  if (it != s->sim_data.signal_map.end()) {
    id = it->second;
    // std::cout << "Found " << path << " with id " << id << std::endl;
  } else {
    // id = search(path);
    // if (id < 0) {
      std::cerr << "Cannot find the object " << path << std::endl;
    // }
  }

  env->ReleaseStringUTFChars(jniPath, path);

  return id;
}

JNIEXPORT jint Java_chisel3_iotesters_TesterSharedLib_getchk(JNIEnv *env, jobject obj, jint id) {
  sim_state *s = get_state(env, obj);

  VerilatorDataWrapper *sig = s->sim_data.signals[id];
  if (!sig) {
    std::cerr << "Cannot find the object of id = " << id << std::endl;
    Java_chisel3_iotesters_TesterSharedLib_finish(env, obj);
    // TODO what?
  } else {
    // std::cout << "Peeking signal " << id << std::endl;
  }
  return sig->get_num_words();
}

}
#endif /* INCLUDE_MAIN */
""")
    codeBuffer.toString()
  }
}

private[iotesters] object setupVerilatorBackend {
  def apply[T <: MultiIOModule](dutGen: () => T, optionsManager: TesterOptionsManager): (T, Backend) = {
    import firrtl.{ChirrtlForm, CircuitState}

    optionsManager.makeTargetDir()

    optionsManager.chiselOptions = optionsManager.chiselOptions.copy(
      runFirrtlCompiler = false
    )

    val dir = new File(optionsManager.targetDirName)

    // Generate CHIRRTL
    chisel3.Driver.execute(optionsManager, dutGen) match {
      case ChiselExecutionSuccess(Some(circuit), emitted, _) =>

        val chirrtl = firrtl.Parser.parse(emitted)
        val dut = getTopModule(circuit).asInstanceOf[T]

        val suppressVerilatorVCD = optionsManager.testerOptions.generateVcdOutput == "off"

        // This makes sure annotations for command line options get created
        val externalAnnotations = firrtl.Driver.getAnnotations(optionsManager)

        /*
        The following block adds an annotation that tells the black box helper where the
        current build directory is, so that it can copy verilog resource files into the right place
         */
        val annotations = externalAnnotations ++ List(BlackBoxTargetDirAnno(optionsManager.targetDirName))

        val transforms = optionsManager.firrtlOptions.customTransforms

        copyVerilatorHeaderFiles(optionsManager.targetDirName)

        // Generate Verilog
        val verilogFile = new File(dir, s"${circuit.name}.v")
        val verilogWriter = new FileWriter(verilogFile)

        val compileResult = (new firrtl.VerilogCompiler).compileAndEmit(
          CircuitState(chirrtl, ChirrtlForm, annotations),
          customTransforms = transforms
        )
        val compiledStuff = compileResult.getEmittedCircuit
        verilogWriter.write(compiledStuff.value)
        verilogWriter.close()

        // Generate Harness
        val cppHarnessFileName = s"${circuit.name}-harness.cpp"
        val cppHarnessFile = new File(dir, cppHarnessFileName)
        val cppHarnessWriter = new FileWriter(cppHarnessFile)
        val vcdFile = new File(dir, s"${circuit.name}.vcd")
        val emittedStuff = VerilatorCppHarnessGenerator.codeGen(
          dut, CircuitState(chirrtl, ChirrtlForm, annotations), vcdFile.toString
        )
        cppHarnessWriter.append(emittedStuff)
        cppHarnessWriter.close()

        val verilatorFlags = optionsManager.testerOptions.moreVcsFlags ++ { if (suppressVerilatorVCD) Seq() else Seq("--trace") }
        assert(
          verilogToVerilator(
            circuit.name,
            dir,
            cppHarnessFile,
            moreVerilatorFlags = verilatorFlags,
            moreVerilatorCFlags = optionsManager.testerOptions.moreVcsCFlags,
            editCommands = optionsManager.testerOptions.vcsCommandEdits
          ).! == 0
        )
        // assert(chisel3.Driver.cppToExe(circuit.name, dir).! == 0)
        assert(cppToSo(circuit.name, dir).! == 0)

        val command = if(optionsManager.testerOptions.testCmd.nonEmpty) {
          optionsManager.testerOptions.testCmd
        }
        else {
          Seq(new File(dir, s"V${circuit.name}").toString)
        }

        (dut, new VerilatorBackend(dut, command))
      case ChiselExecutionFailure(message) =>
        throw new Exception(message)
    }
  }

  def appendSoTarget(prefix: String, dir: File): Unit = {
    Files.write(
      Paths.get(s"${dir.getCanonicalPath()}/V$prefix.mk"),
      (
        s"V${prefix}.dylib: " + 
        "$(VK_USER_OBJS) $(VK_GLOBAL_OBJS) $(VM_PREFIX)__ALL.a\n" +
        "\t$(CC) $(LDFLAGS) -dynamiclib $^ $(LOADLIBES) $(LDLIBS) -o $@ $(LIBS) $(SC_LIBS)"
      ).getBytes(),
      java.nio.file.StandardOpenOption.APPEND
    )
  }
  def cppToSo(prefix: String, dir: File): ProcessBuilder = {
    appendSoTarget(prefix, dir)
    Seq("make", "-C", dir.toString, "-j", "-f", s"V$prefix.mk", s"V$prefix.dylib")
  }
}

private[iotesters] class VerilatorBackend(dut: MultiIOModule,
                                          cmd: Seq[String],
                                          _seed: Long = System.currentTimeMillis) extends Backend(_seed) {

  private[iotesters] val simApiInterface = new SimApiInterface(dut, cmd)

  def poke(signal: InstanceId, value: BigInt, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int) {
    val idx = off map (x => s"[$x]") getOrElse ""
    val path = s"${signal.parentPathName}.${validName(signal.instanceName)}$idx"
    poke(path, value)
  }

  def poke(signal: InstanceId, value: Int, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int) {
    poke(signal, BigInt(value), off)
  }

  def peek(signal: InstanceId, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    val idx = off map (x => s"[$x]") getOrElse ""
    val path = s"${signal.parentPathName}.${validName(signal.instanceName)}$idx"
    val bigIntU = simApiInterface.peek(path) getOrElse BigInt(rnd.nextInt)
  
    def signConvert(bigInt: BigInt, width: Int): BigInt = {
      // Necessary b/c Verilator returns bigInts with whatever # of bits it feels like (?)
      // Inconsistent with getWidth -- note also that since the bigInt is always unsigned,
      // bitLength always gets the max # of bits required to represent bigInt
      val w = bigInt.bitLength.max(width)
      // Negative if MSB is set or in this case, ex: 3 bit wide: negative if >= 4
      if(bigInt >= (BigInt(1) << (w - 1))) bigInt - (BigInt(1) << w) else bigInt
    }

    val result = signal match {
      case s: SInt =>
        signConvert(bigIntU, s.getWidth)
      case f: FixedPoint => signConvert(bigIntU, f.getWidth)
      case _ => bigIntU
    }
    if (verbose) logger info s"  PEEK $path -> ${bigIntToStr(result, base)}"
    result
  }

  def expect(signal: InstanceId, expected: BigInt, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean = {
    val path = s"${signal.parentPathName}.${validName(signal.instanceName)}"

    val got = peek(signal, None)
    val good = got == expected
    if (verbose) logger info (
      s"""${msg}  EXPECT $path -> ${bigIntToStr(got, base)} == """ +
        s"""${bigIntToStr(expected, base)} ${if (good) "PASS" else "FAIL"}""")
    good
  }

  def expect(signal: InstanceId, expected: Int, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean = {
    expect(signal, BigInt(expected), msg)
  }

  def poke(path: String, value: BigInt)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int) {
    if (verbose) logger info s"  POKE $path <- ${bigIntToStr(value, base)}"
    simApiInterface.poke(path, value)
  }

  def poke(path: String, value: Int)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int) {
    poke(path, BigInt(value))
  }

  def peek(path: String)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    val result = simApiInterface.peek(path) getOrElse BigInt(rnd.nextInt)
    if (verbose) logger info s"  PEEK $path -> ${bigIntToStr(result, base)}"
    result
  }

  def expect(path: String, expected: BigInt, msg: => String = "")
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean = {
    val got = simApiInterface.peek(path) getOrElse BigInt(rnd.nextInt)
    val good = got == expected
    if (verbose) logger info (
      s"""${msg}  EXPECT $path got ${bigIntToStr(got, base)} expected""" +
        s"""${bigIntToStr(expected, base)} ${if (good) "PASS" else "FAIL"}""")
    good
  }

  def expect(path: String, expected: Int, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Boolean = {
    expect(path, BigInt(expected), msg)
  }

  def step(n: Int)(implicit logger: TestErrorLog): Unit = {
    simApiInterface.step(n)
  }

  def reset(n: Int = 1): Unit = {
    simApiInterface.reset(n)
  }

  def finish(implicit logger: TestErrorLog): Unit = {
    simApiInterface.finish
  }
}

