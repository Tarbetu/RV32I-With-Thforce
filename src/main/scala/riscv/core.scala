package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Core extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(32.W))
    val output = Output(UInt(32.W))
  })

  val decoder = Module(new Decoder)
  val regFile = Module(new RegFile)
  val alu = Module(new Alu)
  val pc = RegInit(0.U(32.W))

  io.output := 0.U
  regFile.io.rd_write := false.B
  regFile.io.rd_addr := 0.U
  regFile.io.rs1_addr := 0.U
  regFile.io.rs2_addr := 0.U
  alu.io.op := AluOp.None
  alu.io.lhs := 0.U
  alu.io.rhs := 0.U
  regFile.io.rd_data := 0.U

  decoder.io.instruction := io.instruction

  when(decoder.io.aluOp =/= AluOp.None) {
    regFile.io.rs1_addr := decoder.io.rs1
    regFile.io.rd_addr := decoder.io.rd
    alu.io.op := decoder.io.aluOp

    alu.io.lhs := regFile.io.rs1_data

    when(decoder.io.isImmediate) {
      regFile.io.rs2_addr := 0.U

      alu.io.rhs := decoder.io.immediate

    }.otherwise {
      regFile.io.rs2_addr := decoder.io.rs2

      alu.io.rhs := regFile.io.rs2_data

    }

    regFile.io.rd_data := alu.io.out
    regFile.io.rd_write := true.B

    io.output := regFile.io.rd_data

    pc := pc + 4.U
  }.elsewhen(decoder.io.jump) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := pc + 4.U;
    regFile.io.rd_write := true.B

    io.output := pc + decoder.io.immediate_u
    pc := pc + decoder.io.immediate_u
  }.elsewhen(decoder.io.jumpreg) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := pc + 4.U
    regFile.io.rd_write := true.B

    regFile.io.rs1_addr := decoder.io.rs1
    io.output := regFile.io.rs1_data + decoder.io.immediate
    pc := regFile.io.rs1_data + decoder.io.immediate
  }.elsewhen(decoder.io.branch) {
    regFile.io.rs1_addr := decoder.io.rs1
    regFile.io.rs2_addr := decoder.io.rs2

    import BranchType._
    switch(decoder.io.branchType) {
      is(Equal) {
        when(regFile.io.rs1_data === regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(NotEqual) {
        when(regFile.io.rs1_data =/= regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(LessThan) {
        when(regFile.io.rs1_data.asSInt < regFile.io.rs2_data.asSInt) {
          pc := pc + decoder.io.immediate
        }.otherwise { pc := pc + 4.U }
      }
      is(GreaterEqual) {
        when(regFile.io.rs1_data.asSInt >= regFile.io.rs2_data.asSInt) {
          pc := pc + decoder.io.immediate
        }.otherwise { pc := pc + 4.U }
      }
      is(LessThanUnsigned) {
        when(regFile.io.rs1_data < regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(GreaterEqualUnsigned) {
        when(regFile.io.rs1_data >= regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
    }
  }.elsewhen(decoder.io.loadUpperImm) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := decoder.io.immediate_u << 12.U
    regFile.io.rd_write := true.B

    io.output := decoder.io.immediate_u << 12.U
    pc := pc + 4.U
  }.elsewhen(decoder.io.addUpperImmToPc) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := pc + (decoder.io.immediate << 12.U)
    regFile.io.rd_write := true.B

    io.output := pc + (decoder.io.immediate << 12.U)
    pc := pc + 4.U
  }
}
