package riscv

import chisel3._
import chisel3.util._

class RegFile extends Module {
  val io = IO(new Bundle {
    val rs1_addr = Input(UInt(5.W))
    val rs2_addr = Input(UInt(5.W))
    val rs1_data = Output(UInt(31.W))
    val rs2_data = Output(UInt(31.W))

    val rd_addr = Input(UInt(5.W))
    val rd_data = Input(UInt(31.W))
    val rd_write = Input(Bool())
  })

  val registers = Mem(31, UInt(31.W))

  io.rs1_data := Mux(
    io.rs1_addr === 0.U,
    0.U,
    registers.read(io.rs1_addr - 1.U)
  )

  io.rs2_data := Mux(
    io.rs2_addr === 0.U,
    0.U,
    registers.read(io.rs2_addr - 1.U)
  )

  when(io.rd_write && io.rd_addr =/= 0.U) {
    registers.write(io.rd_addr - 1.U, io.rd_data)
  }
}
