package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object Main extends App {
  val withThforce: Boolean = sys.env.get("THFORCE") match {
    case Some(value) => !Set("0", "false").contains(value.trim.toLowerCase)
    case None        => true
  }

  val program = sys.env.get("PROG") match {
    case Some(prog) =>
      prog
    case None =>
      "examples/default.hex"
  }

  ChiselStage.emitSystemVerilogFile(
    new Core(program, withThforce),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable"),
    args = Array("--target-dir", "generated")
  )
}
