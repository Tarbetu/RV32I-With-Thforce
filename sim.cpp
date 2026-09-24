#include "VCore.h"
#include "verilated.h"
#include <memory>

int main(int argc, char** argv) {
  auto contextp = std::make_unique<VerilatedContext>();
  contextp->commandArgs(argc, argv);

  auto top = std::make_unique<VCore>(contextp.get());

  while (!contextp -> gotFinish())
    top->eval();
}
