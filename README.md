**RV32I with THFORCE Instruction**
==============================
This repository contains the CPU core that implements RV32I (the most basic RISC-V processor) and the THFORCE instruction.

# THFORCE Instruction

This is a custom instruction. The name stands for **Th**unk **Force**. A thunk is the data structure used in lazy evaluation, representing an unevaluated computation.

```haskell
Thunk = Suspended function_ptr args
      | Forced value
```

`Suspended` denotes that the thunk is not yet evaluated and `Forced` denotes that the result is ready.

This instruction aims to accelerate the forcing step at the hardware level.

## Usage

```assembly
THFORCE rd, rs1
```

Where:
- `rs1` is the register holding the function pointer
- `rd` is the register holding the **byte address** of where the result is stored. The expected data structure is two consecutive 32-bit words: `fn_ptr` at `rd` and `value` at `rd + 4`

## Behavior

THFORCE has no internal state machine. The phase (entering vs. returning) is derived from the program counter itself: on entry it writes a tagged return address (`pc | 1`) into `x1`, and when the same instruction executes again it recognizes the return by matching `x1` against that tagged value. Because normal return addresses are 4-byte aligned, the tag bit can never collide with a caller's own `x1`.

- On entry, THFORCE checks the function pointer stored at the address in `rd`. If it already equals `rs1`, the thunk is already forced, so the cached value at `rd + 4` is loaded into `rd` and THFORCE advances to the next instruction.
- If it is not yet forced, THFORCE pushes the caller-saved register context (`ra`, `t0–t6`, `a0–a7`) onto the stack using `x2` as the stack pointer, writes the tagged return address to `x1`, and jumps to the function's address. Arguments are passed via `a0–a7` per the standard calling convention.
- After the function returns, THFORCE recognizes the return through the tagged `x1`. It overwrites the `fn_ptr` field at the address in `rd` with the function pointer as a sentinel value (indicating the thunk is now forced), writes the return value from `a0` to `rd + 4` and into the `rd` register, restores the saved register context from the stack, and advances to the next instruction.
- Recursive forcing of a thunk that is still being evaluated is not detected by the hardware; that is left to the program.

# How to Run

## Build

```sh
sbt run
```

This will produce SystemVerilog files under the `generated/` directory. However, it will not be useful since it will not do anything interesting in that state. You have to pass a program to the ROM. You can use this:

``` sh
PROG="generated/with_thforce.hex" sbt run
```

It's worth mentioning that the hardware does not allow loading new programs after the build process, by design. Each instruction has its own separate memory, and the PC only ever points into that memory. If you want to build your own program, you can compile it directly to RV32I assembly.

These are the steps to prepare your program to the ROM.

``` sh
# Assemblying your program
riscv32-none-elf-as -march=rv32i -mabi=ilp32 $PATH_TO_YOUR_ASM -o output.o
# Linking with the linker provided in this repository
riscv32-none-elf-ld -T link.ld output.o -o output.elf
# Converting your object to hex file
riscv32-none-elf-objcopy -O verilog --verilog-data-width=4 output.elf program.hex
# Then pass the location of your hex file
PROG="program.hex" sbt run
```

## Simulation and Comparison

Here is the quick results:
``` sh
--------- Without Thforce ---------
Halted after 48 cycles.
---------  With  Thforce  ---------
Halted after 9 cycles.
```

You can verify the results with the `./verilator_compare` script, which runs the simulation. If you're interested in the simulation artifacts, such as the hex files of the example programs, the Verilator output, and the VCD file, pass the `--keep` flag.

If you pass the `--keep` flag, these files will be preserved which located at `generated` folder:
- **\*.hex** files for providing the output of example programs which you can use
- The binaries compiled by Verilog located at `obj_dir`
- The **\*.vcd** files
- **\*.elf** and **\*.o** for further debug

## Dependencies

You will need these tools:
- Scala 2.13
- sbt
- Verilator (For simulation)
- Surfer or Gtkwave (As a Waveform Viewer)
- RISC-V Toolchain (if you're interested in doing the cycle comparison)

A `shell.nix` file is included for Nix users. If you use `direnv`:

```sh
direnv allow
```

Otherwise:

```sh
nix-shell
```

This shell includes Scala, Metals (Lsp for Scala), Verilator, Surfer and the RiscV Toolchain.
