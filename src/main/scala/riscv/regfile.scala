package riscv

import chisel3._
import chisel3.util._

class RegFile(withThforce: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val rs1Addr = Input(UInt(5.W))
    val rs2Addr = Input(UInt(5.W))
    val rs1Data = Output(UInt(32.W))
    val rs2Data = Output(UInt(32.W))

    val returnAddress = if (withThforce) Some(Input(UInt(32.W))) else None
    val returnAddressWrite = if (withThforce) Some(Input(Bool())) else None
    val returnedValue = if (withThforce) Some(Output(UInt(32.W))) else None

    val rdAddr  = Input(UInt(5.W))
    val rdData  = Input(UInt(32.W))
    val rdWrite = Input(Bool())

    val contextOut   = if (withThforce) Some(Output(Vec(31, UInt(32.W)))) else None
    val contextIn    = if (withThforce) Some(Input(Vec(31, UInt(32.W)))) else None
    val contextWrite = if (withThforce) Some(Input(Bool())) else None
  })

  val registers = Mem(31, UInt(32.W))

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

  when(io.rdWrite && io.rdAddr =/= 0.U) {
    registers.write(io.rdAddr - 1.U, io.rdData)
  }

  if (withThforce) {
    io.returnedValue.get := registers.read(9.U) // a0 - x10

    for (i <- 0 until 31) {
      io.contextOut.get(i) := registers.read(i.U)
    }

    when(io.returnAddressWrite.get) {
      registers.write(0.U, io.returnAddress.get)
    }

    when(io.contextWrite.get) {
      for (i <- 0 until 31) {
        when(!(io.rdWrite && io.rdAddr =/= 0.U && (io.rdAddr - 1.U) === i.U)) {
          registers.write(i.U, io.contextIn.get(i))
        }
      }
    }
  }
}
