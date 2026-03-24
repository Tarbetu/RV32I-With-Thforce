package riscv

import chisel3._
import chisel3.util._

object AluOp extends ChiselEnum {
  val Add, Sub, Xor, Or, And, ShiftRightLogical, ShiftLeftLogical, ShiftRightArith, SetLessThan, SetLessThanUnsigned, None = Value
}

object Opcode extends ChiselEnum {
  val rType = Value(0b0110011.U)
  val imm = Value(0b0010011.U)
  val load = Value(0b0000011.U)
  val store = Value(0b0000011.U)
  val branch = Value(0b1100011.U)
  val jump = Value(0b1101111.U)
  val jumpreg = Value(0b1100111.U)
  val lui = Value(0b0110111.U)
  val auipc = Value(0b0010111.U)
  val environmental = Value(0b1110011.U)
  val fence = Value(0b0001111.U)
}

object LoadSize extends ChiselEnum {
  val Byte, Half, Word, ByteUnsigned, HalfUnsigned = Value
}

object StoreSize extends ChiselEnum {
  val Byte, Half, Word = Value
}

object BranchType extends ChiselEnum {
  val Equal, NotEqual, LessThan, GreaterEqual, LessThanUnsigned, GreaterEqualUnsigned = Value
}
