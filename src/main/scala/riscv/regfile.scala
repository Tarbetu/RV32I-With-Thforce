package riscv

import chisel3._
import chisel3.util._

class RegFile(withThforce: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val rs1Addr = Input(UInt(5.W))
    val rs2Addr = Input(UInt(5.W))
    val rs1Data = Output(UInt(32.W))
    val rs2Data = Output(UInt(32.W))

    val rdAddr = Input(UInt(5.W))
    val rdData = Input(UInt(32.W))
    val rdWrite = Input(Bool())

    // THFORCE related signals
    //
    // This group is for the memory mode
    // If buffer is already occupied, THFORCE instruction will use stack memory to save the context
    val rd2Addr = if (withThforce) Some(Input(UInt(5.W))) else None
    val rd2Data = if (withThforce) Some(Input(UInt(32.W))) else None
    val rd2Write = if (withThforce) Some(Input(Bool())) else None
    val rs3Addr = if (withThforce) Some(Input(UInt(5.W))) else None
    val rs3Data = if (withThforce) Some(Output(UInt(32.W))) else None
    val rs4Addr = if (withThforce) Some(Input(UInt(5.W))) else None
    val rs4Data = if (withThforce) Some(Output(UInt(32.W))) else None
    val deallocSp = if (withThforce) Some(Input(Bool())) else None
    val x1Value = if (withThforce) Some(Output(UInt(32.W))) else None
    val x2Value = if (withThforce) Some(Output(UInt(32.W))) else None
    val returnAddress = if (withThforce) Some(Input(UInt(32.W))) else None
    val returnAddressWrite = if (withThforce) Some(Input(Bool())) else None
    val returnedValue = if (withThforce) Some(Output(UInt(32.W))) else None

    // This group is for buffer mode
    // If buffer is not occupied, THFORCE instruction will use dedicated buffer to save the context
    //
    // useBuffer is a signal to write the context to the buffer
    // When trigerred, buffer will take the values from register.
    // Unlike the memory mode, buffer is located in the RegFile module
    val useBuffer = if (withThforce) Some(Input(Bool())) else None
    // Provides input for bufferAddr, check the bufferAddr
    val bufferAddrIn = if (withThforce) Some(Input(UInt(32.W))) else None
    // Provides the address of the instruction,
    // so that instruction can track whether it's in the buffer mode or not
    val bufferAddrOut = if (withThforce) Some(Output(UInt(32.W))) else None
    val bufferValidOut = if (withThforce) Some(Output(Bool())) else None
    // Signal to loading the buffer into the registers, then resets the signal
    val bufferConsume = if (withThforce) Some(Input(Bool())) else None
  })

  val registers = RegInit(VecInit(Seq.fill(31)(0.U(32.W))))

  // Dedicated register for the buffer mode
  val thunkBuffer = if (withThforce) Some(RegInit(0.U(512.W))) else None
  // Contains the function pointer, it can determine that which thunk uses the buffer
  val bufferAddr = if (withThforce) Some(RegInit(0.U(32.W))) else None
  val bufferValid = if (withThforce) Some(RegInit(false.B)) else None
  // Just concatenates the context into in a value of 512bit
  val contextPackage =
    if (withThforce)
      Some(VecInit(Thforce.savedRegisters.map(reg => registers(reg.litValue.toInt - 1))).asUInt)
    else None

  io.rs1Data := Mux(
    io.rs1Addr === 0.U,
    0.U,
    registers(io.rs1Addr - 1.U)
  )

  io.rs2Data := Mux(
    io.rs2Addr === 0.U,
    0.U,
    registers(io.rs2Addr - 1.U)
  )

  when(io.rdWrite && io.rdAddr =/= 0.U) {
    registers(io.rdAddr - 1.U) := io.rdData
  }

  if (withThforce) {
    io.bufferAddrOut.get := bufferAddr.get
    io.bufferValidOut.get := bufferValid.get
    io.returnedValue.get := registers(9) // a0 - x10
    io.x1Value.get := registers(0)
    io.x2Value.get := registers(1)
    io.rs3Data.get := Mux(
      io.rs3Addr.get === 0.U,
      0.U,
      registers(io.rs3Addr.get - 1.U)
    )
    io.rs4Data.get := Mux(
      io.rs3Addr.get === 0.U,
      0.U,
      registers(io.rs3Addr.get - 1.U)
    )
    when(io.rd2Write.get && io.rd2Addr.get =/= 0.U) {
      registers(io.rd2Addr.get - 1.U) := io.rd2Data.get
    }

    when(io.deallocSp.get) {
      registers(1) := registers(1) + Thforce.frameBytes
    }

    when(io.bufferConsume.get && bufferValid.get) {
      val vals =
        thunkBuffer.get.asTypeOf(Vec(Thforce.savedRegisters.length, UInt(32.W)))

      for ((reg, i) <- Thforce.savedRegisters.zipWithIndex) {
        registers(reg.litValue.toInt - 1) := vals(i)
      }

      bufferAddr.get := 0.U
      bufferValid.get := false.B
    }

    when(io.useBuffer.get) {
      thunkBuffer.get := contextPackage.get
      bufferAddr.get := io.bufferAddrIn.get
      bufferValid.get := true.B
    }

    when(io.returnAddressWrite.get) {
      registers(0) := io.returnAddress.get
    }
  }
}
