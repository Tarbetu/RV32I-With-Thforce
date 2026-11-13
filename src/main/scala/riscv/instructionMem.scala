package riscv

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

class InstructionMem(program: String) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(32.W))
  })

  val mem = SyncReadMem(1024, UInt(32.W))

  loadMemoryFromFile(mem, program)

  io.data := mem(io.addr)
}
