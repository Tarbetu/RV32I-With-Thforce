#include "VCore.h"
#include "verilated.h"
#include <memory>

// We expect to finish it under way under 1000 for this project.
// Let's keep this as a safety limit
constexpr unsigned CYCLE_LIMIT = 1000;

// double sc_time_stamp() {
//   return 0;
// }

int main(int argc, char** argv) {
  // Preferring a heap allocation instead of stack allocation
  // Sometimes, verilator objects might be really "huge" like MBs
  // I'm not sure about the size since
  // the objects like VCore is automatically created by verilator,
  // so I wanted to allocate them to preventing any bad surprises
  auto contextp = std::make_unique<VerilatedContext>();
  contextp->commandArgs(argc, argv);

  auto vcore = std::make_unique<VCore>(contextp.get());

  for (unsigned cycle = 0; (cycle < CYCLE_LIMIT) || !contextp->gotFinish(); cycle++) {
    vcore->eval();
  }
}
