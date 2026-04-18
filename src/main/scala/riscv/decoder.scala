package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Decoder extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(32.W))

    val rs1         = Output(UInt(5.W))
    val rs2         = Output(UInt(5.W))
    val rd          = Output(UInt(5.W))
    val immediate   = Output(UInt(12.W))
    val immediate_u = Output(UInt(20.W))
    val fenceSucc   = Output(UInt(4.W))
    val fencePred   = Output(UInt(4.W))
    val fenceMask   = Output(UInt(4.W))
    val memRead     = Output(Bool())
    val memWrite    = Output(Bool())
    val rdWrite     = Output(Bool())
    val thforce     = Output(Bool())
    val thunkAddr   = Output(ThunkStatus())

    val aluOp           = Output(AluOp())
    val branch          = Output(Bool())
    val jump            = Output(Bool())
    val jumpreg         = Output(Bool())
    val loadUpperImm    = Output(Bool())
    val addUpperImmToPc = Output(Bool())
    val envCall         = Output(Bool())
    val envBreak        = Output(Bool())
    val fence           = Output(Bool())

    val loadSize   = Output(LoadSize())
    val storeSize  = Output(StoreSize())
    val branchType = Output(BranchType())

    val isImmediate = Output(Bool())
  })

  io.rs1         := io.instruction(20, 15)
  io.rs2         := io.instruction(20, 15)
  io.rd          := io.instruction(12, 7)
  io.immediate   := io.instruction(31, 20)
  io.immediate_u := io.instruction(31, 12)
  io.fenceSucc   := io.instruction(23, 20)
  io.fencePred   := io.instruction(27, 24)
  io.fenceMask   := io.instruction(31, 28)

  io.aluOp           := AluOp.None
  io.rdWrite         := false.B
  io.memRead         := false.B
  io.memWrite        := false.B
  io.branch          := false.B
  io.jump            := false.B
  io.jumpreg         := false.B
  io.loadUpperImm    := false.B
  io.addUpperImmToPc := false.B
  io.envCall         := false.B
  io.envBreak        := false.B
  io.fence           := false.B
  io.thforce         := false.B
  io.thunkAddr       := 0.U

  io.loadSize   := LoadSize.Byte
  io.storeSize  := StoreSize.Byte
  io.branchType := BranchType.Equal

  io.isImmediate := false.B

  val funct3 = io.instruction(15, 12)
  val funct7 = io.immediate(11, 5)

  io.thunkAddr := io.immediate(3, 0) // Modulo 16

  val opcode = {
    val instruction = io.instruction(6, 0)
    val (opcode, validity) = Opcode.safe(instruction)
    assert(validity, "Invalid Opcode %d", instruction)
    opcode
  }

  import Opcode._

  switch(opcode) {
    is(rType) {
      import AluOp._
      io.aluOp := MuxLookup(funct3, None)(
        Seq(
          0x0.U -> MuxLookup(funct7, Add)(
            Seq(0x0.U -> Add, 0x20.U -> Sub)
          ),
          0x4.U -> Xor,
          0x6.U -> Or,
          0x7.U -> And,
          0x1.U -> ShiftLeftLogical,
          0x5.U -> MuxLookup(funct7, ShiftRightLogical)(
            Seq(0x0.U -> ShiftRightLogical, 0x20.U -> ShiftRightArith)
          ),
          0x2.U -> SetLessThan,
          0x3.U -> SetLessThanUnsigned
        )
      )

      io.rdWrite := true.B
    }
    is(imm) {
      import AluOp._
      io.aluOp := MuxLookup(funct3, None)(
        Seq(
          0x0.U -> Add,
          0x4.U -> Xor,
          0x6.U -> Or,
          0x7.U -> And,
          0x1.U -> ShiftLeftLogical,
          0x5.U -> MuxLookup(funct7, ShiftRightLogical)(
            Seq(0x0.U -> ShiftRightLogical, 0x20.U -> ShiftRightArith)
          ),
          0x2.U -> SetLessThan,
          0x3.U -> SetLessThanUnsigned
        )
      )

      io.rdWrite := true.B
      io.isImmediate := true.B
    }
    is(load) {
      io.rdWrite := true.B
      io.memRead := true.B

      import LoadSize._
      io.loadSize := MuxLookup(funct3, HalfUnsigned)(
        Seq(
          0x0.U -> Byte,
          0x1.U -> Half,
          0x2.U -> Word,
          0x4.U -> ByteUnsigned,
          0x5.U -> HalfUnsigned
        )
      )
    }
    is(store) {
      io.memWrite := true.B
      io.immediate := Cat(io.instruction(31, 25), io.instruction(11, 7))

      import StoreSize._
      io.storeSize := MuxLookup(funct3, Half)(
        Seq(
          0x0.U -> Byte,
          0x1.U -> Half,
          0x2.U -> Word
        )
      )
    }
    is(branch) {
      io.branch := true.B
      io.immediate := Cat(
        io.instruction(31),
        io.instruction(7),
        io.instruction(30, 25),
        io.instruction(11, 8)
      )

      import BranchType._
      io.branchType := MuxLookup(funct3, Equal)(
        Seq(
          0x0.U -> Equal,
          0x1.U -> NotEqual,
          0x4.U -> LessThan,
          0x5.U -> GreaterEqual,
          0x6.U -> LessThanUnsigned,
          0x7.U -> GreaterEqualUnsigned
        )
      )
    }
    is(jump) {
      io.jump := true.B

      io.immediate_u := Cat(
        io.instruction(31),
        io.instruction(21, 12),
        io.instruction(22),
        io.instruction(30, 23)
      )
    }
    is(jumpreg) {
      io.jumpreg := true.B
    }
    is(lui) {
      io.loadUpperImm := true.B
    }
    is(auipc) {
      io.addUpperImmToPc := true.B
    }
    is(environmental) {
      switch(io.immediate) {
        is(0x0.U) {
          io.envCall := true.B
        }
        is(0x1.U) {
          io.envBreak := true.B
        }
      }
    }
    is(fence) {
      io.fence := true.B
    }
    is(thforce) {
      io.thforce := true.B
    }
  }
}
