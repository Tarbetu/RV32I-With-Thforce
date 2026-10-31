package riscv

import chisel3._
import chisel3.util._

// List of caller-saved registers (per the RISC-V ABI)
// Used for THFORCE.
object Thforce {
  val savedRegisters: Seq[UInt] = Seq(1, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 28, 29, 30, 31).map(_.U)
  val frameBytes = 64.U(32.W)
}

object AluOp extends ChiselEnum {
  val Add, Sub, Xor, Or, And, ShiftRightLogical, ShiftLeftLogical, ShiftRightArith, SetLessThan, SetLessThanUnsigned, None = Value
}

object Opcode extends ChiselEnum {
  val load          = Value(0b0000011.U)
  val thforce       = Value(0b0001011.U)
  val fence         = Value(0b0001111.U)
  val imm           = Value(0b0010011.U)
  val auipc         = Value(0b0010111.U)
  val store         = Value(0b0100011.U)
  val rType         = Value(0b0110011.U)
  val lui           = Value(0b0110111.U)
  val branch        = Value(0b1100011.U)
  val jumpreg       = Value(0b1100111.U)
  val jump          = Value(0b1101111.U)
  val environmental = Value(0b1110011.U)
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
