package riscv

import chisel3._
import chisel3.util._

class Core(program: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val debug = Output(UInt(32.W))
    val halt = Output(Bool())
  })

  val instructionMem = VecInit(program)

  val dataMem = Mem(1024, UInt(32.W))
  val decoder = Module(new Decoder)
  val regFile = Module(new RegFile)
  val alu = Module(new Alu)

  val pc = RegInit(0.U(32.W))

  io.debug := 0.U
  io.halt := false.B

  regFile.io.rdWrite := false.B
  regFile.io.rdAddr := 0.U

  regFile.io.rs1Addr := 0.U
  regFile.io.rs2Addr := 0.U

  regFile.io.thunkAddr := 0.U
  regFile.io.thunkWrite := false.B

  alu.io.op := AluOp.None
  alu.io.lhs := 0.U
  alu.io.rhs := 0.U

  regFile.io.rdData := 0.U

  val instruction = instructionMem(pc)

  decoder.io.instruction := instruction

  when(decoder.io.aluOp =/= AluOp.None) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rdAddr := decoder.io.rd
    alu.io.op := decoder.io.aluOp

    alu.io.lhs := regFile.io.rs1Data

    when(decoder.io.isImmediate) {
      regFile.io.rs2Addr := 0.U

      alu.io.rhs := decoder.io.immediate
    }.otherwise {
      regFile.io.rs2Addr := decoder.io.rs2

      alu.io.rhs := regFile.io.rs2Data
    }

    regFile.io.rdData := alu.io.out
    regFile.io.rdWrite := true.B

    io.debug := regFile.io.rdData

    pc := pc + 4.U
  }.elsewhen(decoder.io.jump) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + 4.U;
    regFile.io.rdWrite := true.B

    io.debug := pc + decoder.io.immediate_u
    pc := pc + decoder.io.immediate_u
  }.elsewhen(decoder.io.jumpreg) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + 4.U
    regFile.io.rdWrite := true.B

    regFile.io.rs1Addr := decoder.io.rs1
    io.debug := regFile.io.rs1Data + decoder.io.immediate
    pc := regFile.io.rs1Data + decoder.io.immediate
  }.elsewhen(decoder.io.branch) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    import BranchType._
    switch(decoder.io.branchType) {
      is(Equal) {
        when(regFile.io.rs1Data === regFile.io.rs2Data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(NotEqual) {
        when(regFile.io.rs1Data =/= regFile.io.rs2Data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(LessThan) {
        when(regFile.io.rs1Data.asSInt < regFile.io.rs2Data.asSInt) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(GreaterEqual) {
        when(regFile.io.rs1Data.asSInt >= regFile.io.rs2Data.asSInt) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(LessThanUnsigned) {
        when(regFile.io.rs1Data < regFile.io.rs2Data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(GreaterEqualUnsigned) {
        when(regFile.io.rs1Data >= regFile.io.rs2Data) {
          pc := pc + decoder.io.immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
    }
  }.elsewhen(decoder.io.loadUpperImm) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := decoder.io.immediate_u << 12.U
    regFile.io.rdWrite := true.B

    io.debug := decoder.io.immediate_u << 12.U
    pc := pc + 4.U
  }.elsewhen(decoder.io.addUpperImmToPc) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + (decoder.io.immediate << 12.U)
    regFile.io.rdWrite := true.B

    io.debug := pc + (decoder.io.immediate << 12.U)
    pc := pc + 4.U
  }.elsewhen(decoder.io.memRead) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rdAddr := decoder.io.rd

    val dataAddr = regFile.io.rs1Data + decoder.io.immediate
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

    regFile.io.rdData := loadData
    regFile.io.rdWrite := true.B

    io.debug := loadData
    pc := pc + 4.U
  }.elsewhen(decoder.io.memWrite) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    val dataAddr = regFile.io.rs1Data + decoder.io.immediate

    import StoreSize._
    val data = MuxLookup(decoder.io.storeSize, regFile.io.rs2Data)(
      Seq(
        Byte -> Cat(
          Fill(24, regFile.io.rs2Data(7)),
          regFile.io.rs2Data(7, 0)
        ),
        Half -> Cat(
          Fill(16, regFile.io.rs2Data(15)),
          regFile.io.rs2Data(15, 0)
        ),
        Word -> regFile.io.rs2Data
      )
    )

    dataMem.write(dataAddr, data)
    io.debug := data
    pc := pc + 4.U
  }.elsewhen(decoder.io.envCall || decoder.io.envBreak) {
    io.halt := true.B
  }.elsewhen(decoder.io.thforce) {
    regFile.io.thunkAddr := decoder.io.thunkAddr
    val thunkStatus = regFile.io.thunkCurrentStatus

    regFile.io.rs1Addr := decoder.io.rs1
    val fn_ptr = regFile.io.rs1Data

    regFile.io.rs2Addr := decoder.io.rd
    val destinationAddr = regFile.io.rs2Data

    import ThunkStatus._

    switch(thunkStatus) {
      is(Idle) {
        val destinationData = dataMem(destinationAddr)

        when(destinationAddr =/= fn_ptr) {
          regFile.io.thunkNewStatus := Visiting
          regFile.io.thunkWrite := true.B
        }.otherwise {
          regFile.io.rdAddr  := decoder.io.rd
          regFile.io.rdData  := dataMem(destinationAddr + 4.U)
          regFile.io.rdWrite := true.B

          pc := pc + 4.U
        }
      }
      is(Visiting) {
        regFile.io.returnAddress := pc
        regFile.io.returnAddressWrite := true.B

        regFile.io.thunkSnapshot := true.B

        pc := fn_ptr
      }
      is(Memorize) {
        dataMem.write(destinationAddr, fn_ptr)
        dataMem.write(destinationAddr + 4.U, regFile.io.returnedValue)

        regFile.io.thunkRestore := true.B

        pc := pc + 4.U
      }
      is(Locked) {
        // Do nothing, go away
        pc := pc + 4.U
      }
    }
  }
}
