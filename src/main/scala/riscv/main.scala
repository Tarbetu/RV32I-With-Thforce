package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object Main extends App {
  val program: Seq[UInt] = sys.env.get("PROG") match {
    case Some(prog) =>
      scala.io.Source.fromFile(prog)
        .getLines()
        .drop(1)
        .flatMap(_.split("\\s+"))
        .filter(_.nonEmpty)
        .map(hex => BigInt(hex, 16).U(32.W))
        .toSeq
    case None =>
      Seq(
        BigInt("00500093", 16).U(32.W),  // addi x1, x0, 5
        BigInt("00A00113", 16).U(32.W),  // addi x2, x0, 10
        BigInt("00100073", 16).U(32.W)   // ebreak
      )
  }

  ChiselStage.emitSystemVerilogFile(
    new Core(program),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable"),
    args = Array("--target-dir", "generated")
  )
}
