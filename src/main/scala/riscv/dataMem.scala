package riscv

import chisel3._
import chisel3.util._

class DataMem() extends Module {
  val io = IO(new Bundle {
    val read = Input(Bool())
    val readAddr = Input(UInt(32.W))
    val dataOut = Output(UInt(32.W))

    val write = Input(Bool())
    val writeAddr = Input(UInt(32.W))
    val dataIn = Input(UInt(32.W))
  })

  val mem = SyncReadMem(1024, UInt(32.W))
  val dataHold = RegInit(0.U(32.W))

  io.dataOut := dataHold

  when(io.read) {
    dataHold := mem(io.readAddr)
  }

  when(io.write) {
    mem.write(io.writeAddr, io.dataIn)
  }
}
