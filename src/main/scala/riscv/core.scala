package riscv

import chisel3._
import chisel3.util._

class Core(program: String, withThforce: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val debug = Output(UInt(32.W))
    val halt = Output(Bool())
  })

  val dataMem = Module(new DataMem())
  val decoder = Module(new Decoder(withThforce))
  val regFile = Module(new RegFile(withThforce))
  val alu = Module(new Alu)
  val instructionMem = Module(new InstructionMem(program))

  val pc = RegInit(0.U(32.W))
  val wordCount = program.length.U

  io.debug := 0.U
  io.halt := false.B

  dataMem.io.read  := false.B
  dataMem.io.write := false.B
  dataMem.io.readAddr := 0.U
  dataMem.io.writeAddr := 0.U
  dataMem.io.dataIn := 0.U

  regFile.io.rdWrite := false.B
  regFile.io.rdAddr := 0.U
  regFile.io.rdData := 0.U

  regFile.io.rs1Addr := 0.U
  regFile.io.rs2Addr := 0.U

  if (withThforce) {
    regFile.io.rs3Addr.get := 0.U
    regFile.io.deallocSp.get := false.B
    regFile.io.returnAddress.get := 0.U
    regFile.io.returnAddressWrite.get := false.B
    regFile.io.useBuffer.get := false.B
    regFile.io.bufferAddrIn.get := 0.U
    regFile.io.bufferConsume.get := false.B
  }

  alu.io.op := AluOp.None
  alu.io.lhs := 0.U
  alu.io.rhs := 0.U

  // A low bit that is always zero in real code addresses since they are 4-byte aligned.
  // It is used to tag the return address written to x1
  // This is the way of how THFORCE tracks the state of thunk
  val lowBitMask = ~0.U(32.W) << 1

  // Check the low-bit Mask
  instructionMem.io.addr := pc >> 2
  val instruction = instructionMem.io.data

  decoder.io.instruction := instruction

  val progReady = RegNext(true.B, false.B)

  val execution = when(!progReady) {
    pc := 0.U
  }.elsewhen(decoder.io.aluOp =/= AluOp.None) {
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
    val jumpTarget = pc + (decoder.io.immediate_u.asSInt.pad(32).asUInt << 1)

    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + 4.U
    regFile.io.rdWrite := true.B
    io.debug := jumpTarget
    pc := jumpTarget
  }.elsewhen(decoder.io.jumpreg) {
    regFile.io.rs1Addr := decoder.io.rs1
    val jumpTarget = (regFile.io.rs1Data + decoder.io.immediate.asSInt.pad(32).asUInt) & lowBitMask

    regFile.io.rdAddr := decoder.io.rd
    regFile.io.rdData := pc + 4.U
    regFile.io.rdWrite := true.B
    io.debug := jumpTarget
    pc := jumpTarget
  }.elsewhen(decoder.io.branch) {
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    import BranchType._

    val immediate = decoder.io.immediate.asSInt.pad(32).asUInt << 1
    val taken = MuxLookup(decoder.io.branchType, false.B)(
      Seq(
        Equal -> (regFile.io.rs1Data === regFile.io.rs2Data),
        NotEqual -> (regFile.io.rs1Data =/= regFile.io.rs2Data),
        LessThan -> (regFile.io.rs1Data.asSInt < regFile.io.rs2Data.asSInt),
        GreaterEqual -> (regFile.io.rs1Data.asSInt >= regFile.io.rs2Data.asSInt),
        LessThanUnsigned -> (regFile.io.rs1Data < regFile.io.rs2Data),
        GreaterEqualUnsigned -> (regFile.io.rs1Data >= regFile.io.rs2Data)
      )
    )

    pc := Mux(taken, pc + immediate, pc + 4.U)
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
    val fetch = RegInit(false.B)
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rdAddr := decoder.io.rd

    val dataAddr =
      regFile.io.rs1Data + decoder.io.immediate.asSInt.pad(32).asUInt
    val wordAddr = dataAddr >> 2

    when(fetch) {
      val byteOff = dataAddr(1, 0)
      val word = dataMem.io.dataOut

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

      fetch := false.B
      pc := pc + 4.U
    }.otherwise {
      dataMem.io.read := true.B
      dataMem.io.readAddr := wordAddr

      fetch := true.B
      pc := pc
    }
  }.elsewhen(decoder.io.memWrite) {
    val fetch = RegInit(false.B)
    regFile.io.rs1Addr := decoder.io.rs1
    regFile.io.rs2Addr := decoder.io.rs2

    val dataAddr =
      regFile.io.rs1Data + decoder.io.immediate.asSInt.pad(32).asUInt
    val wordAddr = dataAddr >> 2
    val byteOff = dataAddr(1, 0)
    val shift = byteOff * 8.U

    val rs2 = regFile.io.rs2Data

    when(fetch) {
      val oldWord = dataMem.io.dataOut

      import StoreSize._
      val sizeMask = MuxLookup(decoder.io.storeSize, "hFFFFFFFF".U(32.W))(
        Seq(
          Byte -> "h000000FF".U(32.W),
          Half -> "h0000FFFF".U(32.W),
          Word -> "hFFFFFFFF".U(32.W)
        )
      )
      val mask = (sizeMask << shift)(31, 0)
      val shifted = (rs2 << shift)(31, 0)
      val newWord = (oldWord & ~mask) | (shifted & mask)

      dataMem.io.write := true.B
      dataMem.io.writeAddr := wordAddr
      dataMem.io.dataIn := newWord
      io.debug := newWord

      fetch := false.B
      pc := pc + 4.U
    }.otherwise {
      dataMem.io.read := true.B
      dataMem.io.readAddr := wordAddr

      fetch := true.B
      pc := pc
    }

  }.elsewhen(decoder.io.envCall || decoder.io.envBreak) {
    io.halt := true.B
  }.elsewhen(decoder.io.fence) {
    pc := pc + 4.U
  }

  if (withThforce) {
    execution.elsewhen(decoder.io.thforce.get) {
      regFile.io.rs1Addr := decoder.io.rs1
      val fnPtr = regFile.io.rs1Data

      regFile.io.rs2Addr := decoder.io.rd
      val destinationWord = regFile.io.rs2Data >> 2

      val spReturningPhrase = RegInit(false.B)
      val bufferReturningPhrase = RegInit(false.B)
      val thunkReturnValue = RegInit(0.U(32.W))

      val thunkCounter = RegInit(0.U(5.W))
      val savedRegs = VecInit(Thforce.savedRegisters)
      val savedCount = (Thforce.savedRegisters.length).U

      val bufferMode = regFile.io.bufferValidOut.get && (regFile.io.bufferAddrOut.get === pc)
      val spMode = regFile.io.x1Value.get === (pc | 1.U)

      when(bufferReturningPhrase) {
        val word = regFile.io.rs2Data >> 2
        val write = RegInit(false.B)

        when(write) {
          dataMem.io.write := true.B
          dataMem.io.writeAddr := word + 1.U
          dataMem.io.dataIn := thunkReturnValue

          regFile.io.rdAddr := decoder.io.rd
          regFile.io.rdData := thunkReturnValue
          regFile.io.rdWrite := true.B
          bufferReturningPhrase := false.B

          write := false.B
          pc := pc + 4.U
        }.otherwise {
          dataMem.io.write := true.B
          dataMem.io.writeAddr := word
          dataMem.io.dataIn := regFile.io.rs1Data

          pc := pc
        }

      }.elsewhen(bufferMode) {
        thunkReturnValue := regFile.io.returnedValue.get
        bufferReturningPhrase := true.B
        regFile.io.bufferConsume.get := true.B
        pc := pc
      }.elsewhen(spMode || spReturningPhrase) {
        when(!spReturningPhrase) {
          thunkReturnValue := regFile.io.returnedValue.get
          spReturningPhrase := true.B
        }

        val oldSp = regFile.io.x2Value.get + Thforce.frameBytes
        val fnPtrValue = Mux(decoder.io.rs1 === 2.U, oldSp, regFile.io.rs1Data)
        val destination = Mux(decoder.io.rd === 2.U, oldSp, regFile.io.rs2Data)
        val word = destination >> 2

        when(thunkCounter === 0.U) {
          dataMem.io.read := true.B
          dataMem.io.readAddr := (regFile.io.x2Value.get >> 2.U)

          thunkCounter := thunkCounter + 1.U
          pc := pc
        }.elsewhen(thunkCounter < savedCount) {
          regFile.io.rdAddr := savedRegs(thunkCounter - 1.U)
          regFile.io.rdData := dataMem.io.dataOut
          regFile.io.rdWrite := true.B

          dataMem.io.read := true.B
          dataMem.io.readAddr := (regFile.io.x2Value.get >> 2.U) + thunkCounter

          thunkCounter := thunkCounter + 1.U
          pc := pc
        }.elsewhen(thunkCounter === savedCount){
          // This branch can be eliminated, I believe
          regFile.io.rdAddr := savedRegs(thunkCounter - 1.U)
          regFile.io.rdData := dataMem.io.dataOut
          regFile.io.rdWrite := true.B

          dataMem.io.write := true.B
          dataMem.io.writeAddr := word
          dataMem.io.dataIn := regFile.io.rs1Data

          thunkCounter := thunkCounter + 1.U
          pc := pc
        }.otherwise {
          dataMem.io.write := true.B
          dataMem.io.writeAddr := word + 1.U
          dataMem.io.dataIn := thunkReturnValue

          regFile.io.rdAddr := decoder.io.rd
          regFile.io.rdData := thunkReturnValue
          regFile.io.rdWrite := true.B
          regFile.io.deallocSp.get := decoder.io.rd =/= 2.U

          spReturningPhrase := false.B
          thunkCounter := 0.U
          pc := pc + 4.U
        }
      }.otherwise {
        val destinationFetch = RegInit(false.B)
        val valueFetch = RegInit(false.B)

        when(destinationFetch) {
          when(valueFetch) {
            regFile.io.rdAddr := decoder.io.rd
            regFile.io.rdData := dataMem.io.dataOut
            regFile.io.rdWrite := true.B

            destinationFetch := false.B
            valueFetch := false.B
            pc := pc + 4.U
          }.elsewhen(dataMem.io.dataOut === fnPtr) {
            dataMem.io.read := true.B
            dataMem.io.readAddr := destinationWord + 1.U

            valueFetch := true.B
            pc := pc
          }.elsewhen(!regFile.io.bufferValidOut.get) {
            regFile.io.bufferAddrIn.get := pc
            regFile.io.useBuffer.get := true.B
            regFile.io.returnAddress.get := pc
            regFile.io.returnAddressWrite.get := true.B

            destinationFetch := false.B
            pc := fnPtr
          }.otherwise {
            val newSp = regFile.io.x2Value.get - Thforce.frameBytes

            when(thunkCounter < savedCount - 1.U) {
              regFile.io.rs3Addr.get := savedRegs(thunkCounter)

              dataMem.io.write := true.B
              dataMem.io.writeAddr := (newSp >> 2) + thunkCounter
              dataMem.io.dataIn := regFile.io.rs3Data.get

              thunkCounter := thunkCounter + 1.U
              pc := pc
            }.otherwise {
              regFile.io.rs3Addr.get := savedRegs(15)

              dataMem.io.write := true.B
              dataMem.io.readAddr := (newSp >> 2) + 15.U
              dataMem.io.dataIn := regFile.io.rs3Data.get

              thunkCounter := 0.U
              regFile.io.returnAddress.get := pc | 1.U
              regFile.io.returnAddressWrite.get := true.B
              regFile.io.rdAddr := 2.U
              regFile.io.rdData := newSp
              regFile.io.rdWrite := true.B

              destinationFetch := false.B
              pc := fnPtr
            }
          }
        }.otherwise {
          dataMem.io.read := true.B
          dataMem.io.readAddr := destinationWord

          destinationFetch := true.B
          pc := pc
        }
      }
    }
  }
}
