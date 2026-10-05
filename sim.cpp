#include "VCore.h"
#include "verilated.h"
#include <memory>
#include <iostream>

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

  unsigned cycle = 0;

  while (cycle < CYCLE_LIMIT && !vcore->io_halt) {
    vcore->clock = 0;
    vcore->eval();

    vcore->clock = 1;
    vcore->eval();

    contextp->timeInc(1);
    cycle++;
  }

  vcore->final();

  std::cout << "Simulation finished after " << cycle << " cycles." << std::endl;
}
