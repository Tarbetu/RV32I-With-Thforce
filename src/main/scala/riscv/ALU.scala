package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Alu extends Module {
  val io = IO(new Bundle {
                val op = Input(AluOp())
                val lhs = Input(UInt(32.W))
                val rhs = Input(UInt(32.W))
                val out = Output(UInt(32.W))
              })

  import AluOp._

  io.out := 0.U

  switch (io.op) {
    is (Add) {
      io.out := io.lhs + io.rhs
    }
    is (Sub) {
      io.out := io.lhs - io.rhs
    }
    is (Xor) {
      io.out := io.lhs ^ io.rhs
    }
    is (Or) {
      io.out := io.lhs | io.rhs
    }
    is (And) {
      io.out := io.lhs & io.rhs
    }
    is (ShiftRightLogical) {
      io.out := io.lhs >> io.rhs(4, 0)
    }
    is (ShiftLeftLogical) {
      io.out := io.lhs << io.rhs(4, 0)
    }
    is (ShiftRightArith) {
      io.out := (io.lhs.asSInt >> io.rhs(4, 0)).asUInt
    }
    is (SetLessThan) {
      io.out := (io.lhs.asSInt < io.rhs.asSInt).asUInt
    }
    is (SetLessThanUnsigned) {
      io.out := (io.lhs < io.rhs).asUInt
    }
  }
}

object Alu extends App {
  ChiselStage.emitSystemVerilogFile(
    new Alu,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}
