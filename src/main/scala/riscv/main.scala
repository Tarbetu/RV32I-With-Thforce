package riscv

import _root_.circt.stage.ChiselStage

object Main extends App {
  ChiselStage.emitSystemVerilogFile(
    new Core,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}
