package riscv

import chisel3._
import chisel3.util._

class RegFile extends Module {
  val io = IO(new Bundle {
    val rs1Addr = Input(UInt(5.W))
    val rs2Addr = Input(UInt(5.W))
    val rs1Data = Output(UInt(32.W))
    val rs2Data = Output(UInt(32.W))

    val returnAddress = Input(UInt(32.W))
    val returnAddressWrite = Input(Bool())
    val returnedValue = Output(UInt(32.W))

    val rdAddr  = Input(UInt(5.W))
    val rdData  = Input(UInt(32.W))
    val rdWrite = Input(Bool())

    val thunkAddr          = Input(UInt(4.W))
    val thunkNewStatus     = Input(ThunkStatus())
    val thunkWrite         = Input(Bool())
    val thunkCurrentStatus = Output(ThunkStatus())
    val thunkSnapshot      = Input(Bool())
    val thunkRestore       = Input(Bool())
  })

  val registers      = Mem(31, UInt(32.W))
  val thunkMem       = Mem(16, ThunkStatus())
  val thunkContextes = Mem(16 * 31, UInt(32.W))

  io.rs1Data := Mux(
    io.rs1Addr === 0.U,
    0.U,
    registers.read(io.rs1Addr - 1.U)
  )

  io.rs2Data := Mux(
    io.rs2Addr === 0.U,
    0.U,
    registers.read(io.rs2Addr - 1.U)
  )

  io.returnedValue := registers.read(9.U) // a0 - x10

  when(io.rdWrite && io.rdAddr =/= 0.U) {
    registers.write(io.rdAddr - 1.U, io.rdData)
  }

  io.thunkCurrentStatus := Mux(
    io.thunkWrite,
    ThunkStatus.Locked,
    thunkMem(io.thunkAddr)
  )

  when(io.thunkWrite) {
    thunkMem.write(io.thunkAddr, io.thunkNewStatus)
  }

  when (io.returnAddressWrite) {
    registers.write(0.U, io.returnAddress)
  }

  when (io.thunkSnapshot) {
    for (i <- 0 until 31) {
      thunkContextes(io.thunkAddr * 31.U + i.U) := registers.read(i.U)
    }
  }

  when (io.thunkRestore) {
    for (i <- 0 until 31) {
      registers.write(i.U, thunkContextes(io.thunkAddr * 31.U + i.U))
    }
  }
}
