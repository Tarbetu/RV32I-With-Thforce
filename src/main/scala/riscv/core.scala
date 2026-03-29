package riscv

import chisel3._
import chisel3.util._

class Core(program: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val output = Output(UInt(32.W))
    val halt = Output(Bool())
  })

  val instructionMem = VecInit(program)

  val dataMem = Mem(1024, UInt(32.W))
  val decoder = Module(new Decoder)
  val regFile = Module(new RegFile)
  val alu = Module(new Alu)
  val pc = RegInit(0.U(32.W))

  io.output := 0.U
  io.halt := false.B
  regFile.io.rd_write := false.B
  regFile.io.rd_addr := 0.U
  regFile.io.rs1_addr := 0.U
  regFile.io.rs2_addr := 0.U
  alu.io.op := AluOp.None
  alu.io.lhs := 0.U
  alu.io.rhs := 0.U
  regFile.io.rd_data := 0.U

  val instruction = instructionMem(pc)

  decoder.io.instruction := instruction

  pc := pc + 4.U

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
        }
      }
      is(NotEqual) {
        when(regFile.io.rs1_data =/= regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }
      }
      is(LessThan) {
        when(regFile.io.rs1_data.asSInt < regFile.io.rs2_data.asSInt) {
          pc := pc + decoder.io.immediate
        }
      }
      is(GreaterEqual) {
        when(regFile.io.rs1_data.asSInt >= regFile.io.rs2_data.asSInt) {
          pc := pc + decoder.io.immediate
        }
      }
      is(LessThanUnsigned) {
        when(regFile.io.rs1_data < regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }
      }
      is(GreaterEqualUnsigned) {
        when(regFile.io.rs1_data >= regFile.io.rs2_data) {
          pc := pc + decoder.io.immediate
        }
      }
    }
  }.elsewhen(decoder.io.loadUpperImm) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := decoder.io.immediate_u << 12.U
    regFile.io.rd_write := true.B

    io.output := decoder.io.immediate_u << 12.U
  }.elsewhen(decoder.io.addUpperImmToPc) {
    regFile.io.rd_addr := decoder.io.rd
    regFile.io.rd_data := pc + (decoder.io.immediate << 12.U)
    regFile.io.rd_write := true.B

    io.output := pc + (decoder.io.immediate << 12.U)
  }.elsewhen(decoder.io.memRead) {
    regFile.io.rs1_addr := decoder.io.rs1
    regFile.io.rd_addr := decoder.io.rd

    val dataAddr = regFile.io.rs1_data + decoder.io.immediate
    val rawData = dataMem(dataAddr)

    import LoadSize._
    val loadData = MuxLookup(decoder.io.loadSize, rawData)(
      Seq(
        Byte -> Cat(Fill(24, rawData(7)), rawData(7, 0)),
        Half -> Cat(Fill(16, rawData(15)), rawData(15, 0)),
        Word -> rawData,
        ByteUnsigned -> Cat(0.U(24.W), rawData(7, 0)),
        HalfUnsigned -> Cat(0.U(16.W), rawData(15, 0))
      )
    )

    regFile.io.rd_data := loadData
    regFile.io.rd_write := true.B

    io.output := loadData
  }.elsewhen(decoder.io.memWrite) {
    regFile.io.rs1_addr := decoder.io.rs1
    regFile.io.rs2_addr := decoder.io.rs2

    val dataAddr = regFile.io.rs1_data + decoder.io.immediate

    import StoreSize._
    val data = MuxLookup(decoder.io.storeSize, regFile.io.rs2_data)(
      Seq(
        Byte -> Cat(
          Fill(24, regFile.io.rs2_data(7)),
          regFile.io.rs2_data(7, 0)
        ),
        Half -> Cat(
          Fill(16, regFile.io.rs2_data(15)),
          regFile.io.rs2_data(15, 0)
        ),
        Word -> regFile.io.rs2_data
      )
    )

    dataMem.write(dataAddr, data)
    io.output := data
  }.elsewhen(decoder.io.envCall || decoder.io.envBreak) {
    io.halt := true.B
  }
}
