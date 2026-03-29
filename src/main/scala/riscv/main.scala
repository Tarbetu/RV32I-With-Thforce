package riscv

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.io.Source.fromFile

object Main extends App {
  val program = if (System.in.available() > 0) {
    scala.io.Source.stdin
      .getLines()
      .filter(_.nonEmpty)
      .map(line => Integer.parseUnsignedInt(line.trim, 16).U(32.W))
      .toSeq
  } else {
    Seq(
      "h00500093".U(32.W),  // addi x1, x0, 5
      "h00A00113".U(32.W),  // addi x2, x0, 10
    )
  }

  ChiselStage.emitSystemVerilogFile(
    new Core(program),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable"),
    args = Array("--target-dir", "generated")
  )
}
