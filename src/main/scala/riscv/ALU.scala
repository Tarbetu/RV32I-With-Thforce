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

  io.out := MuxLookup(io.op, 0.U)(Seq(
    Add                -> (io.lhs + io.rhs),
    Sub                -> (io.lhs - io.rhs),
    Xor                -> (io.lhs ^ io.rhs),
    Or                 -> (io.lhs | io.rhs),
    And                -> (io.lhs & io.rhs),
    ShiftRightLogical  -> (io.lhs >> io.rhs(4, 0)),
    ShiftLeftLogical   -> (io.lhs << io.rhs(4, 0)),
    ShiftRightArith    -> (io.lhs.asSInt >> io.rhs(4, 0)).asUInt,
    SetLessThan        -> (io.lhs.asSInt < io.rhs.asSInt).asUInt,
    SetLessThanUnsigned -> (io.lhs < io.rhs).asUInt,
  ))
}

// object Alu extends App {
//   ChiselStage.emitSystemVerilogFile(
//     new Alu,
//     firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
//   )
// }
