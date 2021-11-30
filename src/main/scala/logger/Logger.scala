// SPDX-License-Identifier: Apache-2.0

package logger

import java.io.{ByteArrayOutputStream, File, FileOutputStream, PrintStream}

import firrtl.{AnnotationSeq, ExecutionOptionsManager}
import firrtl.AnnotationSeq
import firrtl.options.Viewer.view
import logger.phases.{AddDefaults, Checks}

object LoggerCompatibility {

  /**
     * This creates a block of code that will have access to the
     * thread specific logger.  The state will be set according to the
     * logging options set in the common options of the manager
     * @param manager  source of logger settings
     * @param codeBlock      code to be run with these logger settings
     * @tparam A       The return type of codeBlock
     * @return         Whatever block returns
     */
   @deprecated("Use makeScope(opts: FirrtlOptions)", "FIRRTL 1.2")
   def makeScope[A](manager: ExecutionOptionsManager)(codeBlock: => A): A =
     Logger.makeScope(manager.commonOptions.toAnnotations)(codeBlock)

   /**
     * See makeScope using manager.  This creates a manager from a command line arguments style
     * list of strings
     * @param args List of strings
     * @param codeBlock  the block to call
     * @tparam A   return type of codeBlock
     * @return
     */
   @deprecated("Use makescope(opts: FirrtlOptions)", "FIRRTL 1.2")
   def makeScope[A](args: Array[String] = Array.empty)(codeBlock: => A): A = {
     val executionOptionsManager = new ExecutionOptionsManager("logger")
     if (executionOptionsManager.parse(args)) {
       makeScope(executionOptionsManager)(codeBlock)
     } else {
       throw new Exception(s"logger invoke failed to parse args ${args.mkString(", ")}")
     }
   }
}

