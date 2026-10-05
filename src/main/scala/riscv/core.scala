package riscv

import chisel3._
import chisel3.util._

class Core(program: Seq[UInt]) extends Module {
  val io = IO(new Bundle {
    val debug = Output(UInt(32.W))
    val halt = Output(Bool())
  })

  val instructionMem = VecInit(program)

  val dataMem = Mem(1024, Vec(4, UInt(8.W)))
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
  regFile.io.returnAddress := 0.U
  regFile.io.returnAddressWrite := false.B
  regFile.io.thunkNewStatus := ThunkStatus.Idle
  regFile.io.thunkSnapshot := false.B
  regFile.io.thunkRestore := false.B

  alu.io.op := AluOp.None
  alu.io.lhs := 0.U
  alu.io.rhs := 0.U

  regFile.io.rdData := 0.U

  // An instruction is 32-bit for simplicity, but an usual RiscV CPU is byte-addressable
  // There can be many programs that can treat the program as byte-addressable
  // So, we're just dividing the pc with 4.
  val instruction = instructionMem(pc >> 2)

  decoder.io.instruction := instruction

  when(decoder.io.aluOp =/= AluOp.None) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rdAddr := decoder.io.rd
    alu.io.op := decoder.io.aluOp

    alu.io.lhs := regFile.io.rs1Data

    when(decoder.io.isImmediate) {
      regFile.io.rs2Addr := 0.U

      alu.io.rhs := decoder.io.immediate.asSInt.pad(32).asUInt
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

    val jumpOffset = decoder.io.immediate_u.asSInt.pad(32).asUInt << 1

    io.debug := pc + jumpOffset
    pc := pc + jumpOffset
  }.elsewhen(decoder.io.jumpreg) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + 4.U
    regFile.io.rdWrite := true.B

    regFile.io.rs1Addr := decoder.io.rs1
    io.debug := regFile.io.rs1Data + (decoder.io.immediate.asSInt.pad(32).asUInt & ~1.U)
    pc := regFile.io.rs1Data + (decoder.io.immediate.asSInt.pad(32).asUInt & ~1.U)
  }.elsewhen(decoder.io.branch) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    import BranchType._

    val immediate = decoder.io.immediate.asSInt.pad(32).asUInt << 1

    switch(decoder.io.branchType) {
      is(Equal) {
        when(regFile.io.rs1Data === regFile.io.rs2Data) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(NotEqual) {
        when(regFile.io.rs1Data =/= regFile.io.rs2Data) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(LessThan) {
        when(regFile.io.rs1Data.asSInt < regFile.io.rs2Data.asSInt) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(GreaterEqual) {
        when(regFile.io.rs1Data.asSInt >= regFile.io.rs2Data.asSInt) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(LessThanUnsigned) {
        when(regFile.io.rs1Data < regFile.io.rs2Data) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(GreaterEqualUnsigned) {
        when(regFile.io.rs1Data >= regFile.io.rs2Data) {
          pc := pc + immediate
        }.otherwise {
          pc := pc + 4.U
        }
      }
    }
  }.elsewhen(decoder.io.loadUpperImm) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := decoder.io.immediate_u.asSInt.pad(32).asUInt << 12.U
    regFile.io.rdWrite := true.B

    io.debug := decoder.io.immediate_u.asSInt.pad(32).asUInt << 12.U
    pc := pc + 4.U
  }.elsewhen(decoder.io.addUpperImmToPc) {
    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + (decoder.io.immediate_u << 12.U)
    regFile.io.rdWrite := true.B

    io.debug := pc + (decoder.io.immediate_u << 12.U)
    pc := pc + 4.U
  }.elsewhen(decoder.io.memRead) {
    regFile.io.rs1Addr := decoder.io.rs1

    regFile.io.rdAddr := decoder.io.rd

    val dataAddr = regFile.io.rs1Data + decoder.io.immediate.asSInt.pad(32).asUInt
    val wordAddr = dataAddr >> 2
    val byteOff  = dataAddr(1, 0)

    val wordVec = dataMem(wordAddr)
    val word = Cat(wordVec(3), wordVec(2), wordVec(1), wordVec(0))

    val byteData = MuxLookup(byteOff, word(7, 0))(
      Seq(
        0.U -> word(7, 0),
        1.U -> word(15, 8),
        2.U -> word(23, 16),
        3.U -> word(31, 24)
      )
    )
    val halfData = Mux(byteOff(1), word(31, 16), word(15, 0))

    import LoadSize._
    val loadData = MuxLookup(decoder.io.loadSize, word)(
      Seq(
        Byte -> Cat(Fill(24, byteData(7)), byteData),
        Half -> Cat(Fill(16, halfData(15)), halfData),
        Word -> word,
        ByteUnsigned -> Cat(0.U(24.W), byteData),
        HalfUnsigned -> Cat(0.U(16.W), halfData)
      )
    )

    regFile.io.rdData := loadData
    regFile.io.rdWrite := true.B

    io.debug := loadData
    pc := pc + 4.U
  }.elsewhen(decoder.io.memWrite) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    val dataAddr = regFile.io.rs1Data + decoder.io.immediate.asSInt.pad(32).asUInt
    val wordAddr = dataAddr >> 2
    val byteOff  = dataAddr(1, 0)

    val rs2     = regFile.io.rs2Data
    val oldVec  = dataMem(wordAddr)
    val oldWord = Cat(oldVec(3), oldVec(2), oldVec(1), oldVec(0))

    val wordMask = VecInit(true.B, true.B, true.B, true.B)
    val halfMask = Mux(
      byteOff(1),
      VecInit(false.B, false.B, true.B, true.B),
      VecInit(true.B, true.B, false.B, false.B)
    )
    val byteMask = MuxLookup(byteOff, VecInit(false.B, false.B, false.B, false.B))(
      Seq(
        0.U -> VecInit(true.B, false.B, false.B, false.B),
        1.U -> VecInit(false.B, true.B, false.B, false.B),
        2.U -> VecInit(false.B, false.B, true.B, false.B),
        3.U -> VecInit(false.B, false.B, false.B, true.B)
      )
    )

    import StoreSize._
    val mask = MuxLookup(decoder.io.storeSize, wordMask)(
      Seq(
        Byte -> byteMask,
        Half -> halfMask,
        Word -> wordMask
      )
    )

    val newBytes = VecInit(
      Mux(mask(0), rs2(7, 0), oldWord(7, 0)),
      Mux(mask(1), rs2(15, 8), oldWord(15, 8)),
      Mux(mask(2), rs2(23, 16), oldWord(23, 16)),
      Mux(mask(3), rs2(31, 24), oldWord(31, 24))
    )

    dataMem.write(wordAddr, newBytes)
    io.debug := Cat(newBytes(3), newBytes(2), newBytes(1), newBytes(0))
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

    val destinationWord = destinationAddr >> 2

    switch(thunkStatus) {
      is(Idle) {
        val destinationVec = dataMem(destinationWord)
        val destinationData =
          Cat(destinationVec(3), destinationVec(2), destinationVec(1), destinationVec(0))

        when(destinationData =/= fn_ptr) {
          regFile.io.thunkNewStatus := Visiting
          regFile.io.thunkWrite := true.B
        }.otherwise {
          pc := pc + 4.U
        }
      }
      is(Visiting) {
        regFile.io.returnAddress := pc
        regFile.io.returnAddressWrite := true.B

        regFile.io.thunkSnapshot := true.B
        regFile.io.thunkNewStatus := Memorize
        regFile.io.thunkWrite := true.B

        pc := fn_ptr
      }
      is(Memorize) {
        val ptrLanes = VecInit(
          fn_ptr(7, 0),
          fn_ptr(15, 8),
          fn_ptr(23, 16),
          fn_ptr(31, 24)
        )
        val valueLanes = VecInit(
          regFile.io.returnedValue(7, 0),
          regFile.io.returnedValue(15, 8),
          regFile.io.returnedValue(23, 16),
          regFile.io.returnedValue(31, 24)
        )

        dataMem.write(destinationWord, ptrLanes)
        dataMem.write(destinationWord + 1.U, valueLanes)

        regFile.io.thunkRestore := true.B

        regFile.io.thunkNewStatus := Idle
        regFile.io.thunkWrite := true.B

        val resultVec = dataMem((destinationAddr + 4.U) >> 2)

        regFile.io.rdAddr  := decoder.io.rd
        regFile.io.rdData  := Cat(resultVec(3), resultVec(2), resultVec(1), resultVec(0))
        regFile.io.rdWrite := true.B

        pc := pc + 4.U
      }
    }
  }
}
