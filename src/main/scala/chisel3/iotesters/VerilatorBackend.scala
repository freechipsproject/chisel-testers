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
        codeBuffer.append(s"        sim_data.$vector.push_back(new VerilatorCData(&($pathName)));\n")
      } else if (width <= 16) {
        codeBuffer.append(s"        sim_data.$vector.push_back(new VerilatorSData(&($pathName)));\n")
      } else if (width <= 32) {
        codeBuffer.append(s"        sim_data.$vector.push_back(new VerilatorIData(&($pathName)));\n")
      } else if (width <= 64) {
        codeBuffer.append(s"        sim_data.$vector.push_back(new VerilatorQData(&($pathName)));\n")
      } else {
        val numWords = (width-1)/32 + 1
        codeBuffer.append(s"        sim_data.$vector.push_back(new VerilatorWData($pathName, $numWords));\n")
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
class $dutApiClassName: public sim_api_t<VerilatorDataWrapper*> {
    public:
    $dutApiClassName($dutVerilatorClassName* _dut) {
        dut = _dut;
        main_time = 0L;
        is_exit = false;
#if VM_TRACE
        tfp = NULL;
#endif
    }
    void init_sim_data() {
        sim_data.inputs.clear();
        sim_data.outputs.clear();
        sim_data.signals.clear();

""")
    inputs.toList foreach { case (node, name) =>
      // replaceFirst used here in case port name contains the dutName
      pushBack("inputs", name replaceFirst (dutName, "dut"), node.getWidth)
    }
    outputs.toList foreach { case (node, name) =>
      // replaceFirst used here in case port name contains the dutName
      pushBack("outputs", name replaceFirst (dutName, "dut"), node.getWidth)
    }
    pushBack("signals", "dut->reset", 1)
    codeBuffer.append(s"""        sim_data.signal_map["${dut.reset.pathName}"] = 0;
    }
#if VM_TRACE
     void init_dump(VerilatedVcdC* _tfp) { tfp = _tfp; }
#endif
    inline bool exit() { return is_exit; }

    // required for sc_time_stamp()
    virtual inline double get_time_stamp() {
        return main_time;
    }

    private:
    ${dutVerilatorClassName}* dut;
    bool is_exit;
    vluint64_t main_time;
#if VM_TRACE
    VerilatedVcdC* tfp;
#endif
    virtual inline size_t put_value(VerilatorDataWrapper* &sig, uint64_t* data, bool force=false) {
        return sig->put_value(data);
    }
    virtual inline size_t get_value(VerilatorDataWrapper* &sig, uint64_t* data) {
        return sig->get_value(data);
    }
    virtual inline size_t get_chunk(VerilatorDataWrapper* &sig) {
        return sig->get_num_words();
    }
    virtual inline void reset() {
        dut->reset = 1;
        step();
    }
    virtual inline void start() {
        dut->reset = 0;
    }
    virtual inline void finish() {
        dut->eval();
        is_exit = true;
    }
    virtual inline void step() {
        dut->clock = 0;
        dut->eval();
#if VM_TRACE
        if (tfp) tfp->dump(main_time);
#endif
        main_time++;
        dut->clock = 1;
        dut->eval();
#if VM_TRACE
        if (tfp) tfp->dump(main_time);
#endif
        main_time++;
    }
    virtual inline void update() {
        dut->_eval_settle(dut->__VlSymsp);
    }
};

// The following isn't strictly required unless we emit (possibly indirectly) something
// requiring a time-stamp (such as an assert).
static ${dutApiClassName} * _Top_api;
double sc_time_stamp () { return _Top_api->get_time_stamp(); }

// Override Verilator definition so first $$finish ends simulation
// Note: VL_USER_FINISH needs to be defined when compiling Verilator code
void vl_finish(const char* filename, int linenum, const char* hier) {
  Verilated::flushCall();
  exit(0);
}

#ifdef INCLUDE_MAIN
int main(int argc, char **argv, char **env) {
    Verilated::commandArgs(argc, argv);
    $dutVerilatorClassName* top = new $dutVerilatorClassName;
    std::string vcdfile = "${vcdFilePath}";
    std::vector<std::string> args(argv+1, argv+argc);
    std::vector<std::string>::const_iterator it;
    for (it = args.begin() ; it != args.end() ; it++) {
        if (it->find("+waveform=") == 0) vcdfile = it->c_str()+10;
    }
#if VM_TRACE
    Verilated::traceEverOn(true);
    VL_PRINTF(\"Enabling waves..\");
    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open(vcdfile.c_str());
#endif
    ${dutApiClassName} api(top);
    _Top_api = &api; /* required for sc_time_stamp() */
    api.init_sim_data();
    api.init_channels();
#if VM_TRACE
    api.init_dump(tfp);
#endif
    while(!api.exit()) api.tick();
#if VM_TRACE
    if (tfp) tfp->close();
    delete tfp;
#endif
    delete top;
    exit(0);
}
int main(int argc, char **argv, char **env) {
  entry(argc, char**argv, char **env);
}
#else /* INCLUDE_MAIN */
#include <jni.h>

static $dutVerilatorClassName* dut = NULL;
static VerilatedVcdC* tfp = NULL;
static vluint64_t main_time = 0;
static sim_data_t<VerilatorDataWrapper*> sim_data;

extern "C" {
JNIEXPORT void JNICALL Java_chisel3_iotesters_TesterSharedLib_sim_1init(JNIEnv *env, jobject obj) {
    // Verilated::commandArgs(argc, argv);
    dut = new $dutVerilatorClassName;
    std::string vcdfile = "${vcdFilePath}";
    // std::vector<std::string> args(argv+1, argv+argc);
    // std::vector<std::string>::const_iterator it;
    // for (it = args.begin() ; it != args.end() ; it++) {
    //     if (it->find("+waveform=") == 0) vcdfile = it->c_str()+10;
    // }
#if VM_TRACE
    Verilated::traceEverOn(true);
    VL_PRINTF(\"Enabling waves..\");
    tfp = new VerilatedVcdC;
    main_time = 0;
    dut->trace(tfp, 99);
    tfp->open(vcdfile.c_str());
#endif
  sim_data.inputs.clear();
  sim_data.outputs.clear();
  sim_data.signals.clear();

""")
    var signalMapCnt = 0
  inputs.toList foreach { case (node, name) =>
    // TODO this won't work if circuit name has underscore in it
    val mapName = node.pathName.replace(".", "_").replaceFirst("_", ".")
    // replaceFirst used here in case port name contains the dutName
    pushBack("signals", name replaceFirst (dutName, "dut"), node.getWidth)
    codeBuffer.append(s"""        sim_data.signal_map["$mapName"] = $signalMapCnt;""")
    signalMapCnt += 1
  }
  outputs.toList foreach { case (node, name) =>
    val mapName = node.pathName.replace(".", "_").replaceFirst("_", ".")
    // replaceFirst used here in case port name contains the dutName
    pushBack("signals", name replaceFirst (dutName, "dut"), node.getWidth)
    codeBuffer.append(s"""        sim_data.signal_map["$mapName"] = $signalMapCnt;""")
    signalMapCnt += 1
  }
  pushBack("signals", "dut->reset", 1)
  codeBuffer.append(s"""        sim_data.signal_map["${dut.reset.pathName}"] = $signalMapCnt;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_step(JNIEnv *env, jobject obj) {
  // std::cout << "Stepping" << std::endl;
  dut->clock = 0;
  dut->eval();
#if VM_TRACE
  if (tfp) tfp->dump(main_time);
#endif /* VM_TRACE */
  dut->clock = 1;
  dut->eval();
#if VM_TRACE
  if (tfp) tfp->dump(main_time);
#endif /* VM_TRACE */
  main_time++;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_reset(JNIEnv *env, jobject obj) {
  dut->reset = 1;
  Java_chisel3_iotesters_TesterSharedLib_step(env, obj);
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_update(JNIEnv *env, jobject obj) {
  dut->_eval_settle(dut->__VlSymsp);
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_start(JNIEnv *env, jobject obj) {
  dut->reset = 0;
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_finish(JNIEnv *env, jobject obj) {
  dut->eval();
}

JNIEXPORT void Java_chisel3_iotesters_TesterSharedLib_poke(JNIEnv *env, jobject obj, jint id, jint value) {
  VerilatorDataWrapper *sig = sim_data.signals[id];
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
  VerilatorDataWrapper *sig = sim_data.signals[id];
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
  const char *path = env->GetStringUTFChars(jniPath, NULL);

  std::map<std::string, size_t>::iterator it;

  it = sim_data.signal_map.find(path);
  jint id = -1;

  if (it != sim_data.signal_map.end()) {
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
  VerilatorDataWrapper *sig = sim_data.signals[id];
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

