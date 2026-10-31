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
    val deallocSp = if (withThforce) Some(Input(Bool)) else None
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
    // Signal to loading the buffer into the registers, then resets the signal
    val bufferConsume = if (withThforce) Some(Input(Bool())) else None
  })

  val registers = RegInit(VecInit(Seq.fill(31)(0.U(32.W))))

  // Dedicated register for the buffer mode
  val thunkBuffer = if (withThforce) Some(RegInit(0.U(512.W))) else None
  // Contains the function pointer, it can determine that which thunk uses the buffer
  val bufferAddr = if (withThforce) Some(RegInit(0.U(32.W))) else None
  // Just concatenates the context into in a value of 512bit
  val contextPackage =
    if (withThforce) Some(Cat(Thforce.savedRegisters.map(registers(_))).asUInt)
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
    registers(io.rdAddr - 1.U, io.rdData)
  }

  if (withThforce) {
    io.bufferAddrOut.get := bufferAddr.get
    io.returnedValue.get := registers(9) // a0 - x10

    when(deallocSp) {
      registers(2) := registers(2) + Thforce.frameBytes
    }

    when(io.bufferConsume.get && bufferAddr.get =/= 0.U) {
      val vals =
        thunkBuffer.asTypeOf(Vec(Thunk.savedRegisters.length, UInt(32.W)))
      for ((reg, i) <- Thforce.savedRegisters.zipWithIndex) {
        registers(reg) := vals(i)
      }
      bufferAddr.get := 0.U
      io.bufferAddrOut.get := 0.U
    }

    when(io.useBuffer.get) {
      thunkBuffer.get := contextPackage.get
      bufferAddr.get := io.bufferAddrIn.get
      io.bufferAddrOut.get := 0.U
    }

    when(io.returnAddressWrite.get) {
      registers(0, io.returnAddress.get)
    }
  }
}
