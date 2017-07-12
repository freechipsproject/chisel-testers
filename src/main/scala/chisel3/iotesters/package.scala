package chisel3

import chisel3.core.LegacyModule

package object iotesters {
  // We don't want firrtl complaining about "not fully initialized" connections.
  val implicitInvalidateOptions = chisel3.core.ExplicitCompileOptions.Strict.copy(explicitInvalidate = false)
  abstract class ImplicitInvalidateModule extends LegacyModule()(implicitInvalidateOptions)
}
