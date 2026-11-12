package riscv

import chisel3._
import chisel3.util._

class InstructionMem(program: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(32.W))
  })

  val nop = 0x00000013.U(32.W)

  if (program.isEmpty) {
    io.data := nop
  } else if (program.size == 1) {
    io.data := Mux(io.addr === 0.U, program.head, nop)
  } else {
    val idxBits = log2Ceil(program.size)
    val padded = program.padTo(1 << idxBits, nop)
    io.data := VecInit(padded)(io.addr(idxBits - 1, 0))
  }
}
