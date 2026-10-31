package riscv

import chisel3._
import chisel3.util._

class InstructionMem(program: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(32.W))
  })

  if (program.size == 1) {
    io.data := program.head
  } else {
    io.data := VecInit(program)(io.addr(log2Ceil(program.size) - 1, 0))
  }
}
