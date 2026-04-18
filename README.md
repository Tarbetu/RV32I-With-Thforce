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
THFORCE rs1, rd, id
```

Where:
- `rs1` is the register holding the function pointer
- `rd` is the register holding the pointer to where the result will be stored. The expected data structure at that address is `{fn_ptr, value}`
- `id` is the immediate value identifying the thunk. There can be at most 16 thunks, with IDs starting from `0`

## Behavior

- THFORCE checks the status of the thunk with the given `id`.
- If it is in the **Idle** state, it checks the function pointer at the address in `rd`. If it matches `rs1`, the thunk is already forced and the value is ready — THFORCE reads the result and writes it to `rd`.
- If it is not yet forced, THFORCE moves into the **Visiting** state. It saves the current register context to the thunk's dedicated memory, writes the return address to `x1`, and jumps to the function's address. Arguments are passed via `a0–a7` per the standard calling convention.
- If the thunk is currently being evaluated (e.g. due to a recursive call), it is in the **Locked** state and THFORCE does nothing.
- After the function returns, THFORCE enters the **Memorize** state. It overwrites the `fn_ptr` field at the address in `rd` with the function pointer as a sentinel value (indicating the thunk is now forced), writes the return value from `a0` to the next word, restores the saved register context, and transitions back to **Idle**.

# How to Run

## Build

```sh
sbt run
```

This will produce SystemVerilog files under the [generated](/generated) directory.

## Test

```sh
sbt test
```

## Dependencies

- Scala 2.13
- sbt
- Metals (for development)
- Yosys (optional, for synthesis)
- Verilator (optional, for simulation)

A `shell.nix` file is included for Nix users. If you use `direnv`:

```sh
direnv allow
```

Otherwise:

```sh
nix-shell
```
