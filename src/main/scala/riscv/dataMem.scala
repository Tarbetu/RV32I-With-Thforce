package riscv

import chisel3._
import chisel3.util._

class dataMem() extends Module {
  val io = IO(new Bundle {
    val op1 = Input(MemOp())
    val addr1 = Input(UInt(32.W))
    val dataIn1  = Input(UInt(32.W))
    val dataOut1 = Output(UInt(32.W))

    val op2 = Input(MemOp())
    val addr2 = Input(UInt(32.W))
    val dataIn2  = Input(UInt(32.W))
    val dataOut2 = Output(UInt(32.W))
  })

  val mem = SyncReadMem(1024, UInt(32.W))

  val dataHold1 = RegInit(0.U)
  val dataHold2 = RegInit(0.U)
  io.dataOut1 := dataHold1
  io.dataOut2 := dataHold2

  when(io.op1 =/= MemOp.None) {
    val port = mem(io.addr1)

    when(io.op1 === MemOp.Write) {
      when(addr1 != addr2) {
        port := io.dataIn1
      }
    }.otherwise {
      dataHold1 := port
    }
  }

  when(io.op2 != MemOp.None) {
    val port = mem(io.addr2)

    when(io.op2 === MemOp.Write) {
      when(addr1 != addr2) {
        port := io.dataIn2
      }
    }.otherwise {
      dataHold2 := port
    }
  }
}
