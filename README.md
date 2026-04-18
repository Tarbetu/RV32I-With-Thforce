RV32I with THFORCE Instruction
==============================
This repository contains the CPU core that implements the RV32I (The most basic RiscV Processor) and THFORCE instruction set.

# THFORCE instruction?
This is my own home-baked instruction. The name stands for "**Th***unk **Force**. Thunk is the data structure used in Lazy Evaluation and it's purpose is representing the unevaluated context.

``` haskell
Thunk = Suspended function_ptr args
      | Forced value
```
`Suspended` denotes that it is not evaluated yet and `Forced` denotes that the result is ready.

This instruction aims to help the forcing step in the hardware level.

## The Usage

``` assembly
THFORCE rs1, rd, id
```
Where
- `rs1` is the register which has function pointer
- `rd`  is the register which has pointer of where the result data will stored
  The expected data structure is `{fn_ptr, value}`
- `id`  is the immediate value of the thunk

## What happens when the instruction called
- THFORCE will check the status of thunk with the id. There can be only 16 thunks, and the id's are starts with `0`.
- If it's in the "Idle" state, it will check the function pointer in the `rd`. If it's same with function pointer, it means that the thunk is forced and value is ready.
- If it is not forced, it will move into "Visiting" state, it will write the THFORCE's instruction address to the `x0` and save the registers to the thunks memory. Then PC will move to the function's address. The thunk will wait in the "Memorize" state. The arguments in a0-a7 will passed to the function.
- After the execution, the hardware will copy the function pointer to the pointer from `rd` for denoting that it's forced and the `a0` (The return address) to the next place of that pointer. Then, the thunk will wait in the idle state.

# How to run?
## The command to run
``` sh
sbt run
```

This code will produce the SystemVerilog files under [generated](/generated) directory.
## The command to test

``` sh
sbt test
```

## Dependencies
- Scala 2.13
- sbt
- Metals (for development)
- Yosys (optional, for synthesis)
- Verilator (optional, for testing)

This repository also contains a shell.nix file if you're using Nix and you feel to lazy to catch the dependencies. If you're using `direnv` (Which means that), just type this command to install the dependencies.
``` sh
direnv allow
```
Otherwise, you know the way

``` sh
nix-shell
```
