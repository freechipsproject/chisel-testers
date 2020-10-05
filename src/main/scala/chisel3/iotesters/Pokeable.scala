// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import chisel3.Bits
import chisel3.core.{Element, EnumType}
import scala.annotation.implicitNotFound

// A typeclass that defines the types we can poke, peek, or expect from
@implicitNotFound("Cannot peek or poke elements of type ${T}")
trait Pokeable[-T]

object Pokeable {
  implicit object BitsPokeable extends Pokeable[Bits]
  implicit object EnumPokeable extends Pokeable[EnumType]

  trait IsRuntimePokeable // A trait that is applied to elements that were proven to be pokeable at runtime (usually in match statements)
  implicit object RuntimePokeable extends Pokeable[IsRuntimePokeable]

  def unapply(elem: Element): Option[Element with IsRuntimePokeable] = elem match {
    case _: Bits | _: EnumType => Some(elem.asInstanceOf[Element with IsRuntimePokeable])
    case _ => None
  }
}
