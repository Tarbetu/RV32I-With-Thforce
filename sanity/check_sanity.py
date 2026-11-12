#!/usr/bin/env python3
# This script is... Well... Produced by AI.
# I don't like the "Vibe coding" thing.
# I was lazy to adding a test suite to being sure about my design.
# Instead, I beg my agent to ensure my design.
# It come up with these lines, and I decided to keep them.

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GENERATED = ROOT / "generated"
TESTS_DIR = ROOT / "tests"

AS = "riscv32-none-elf-as"
LD = "riscv32-none-elf-ld"
OBJCOPY = "riscv32-none-elf-objcopy"
NM = "riscv32-none-elf-nm"
VERILATOR = os.environ.get("VERILATOR", "verilator")

TESTS = {
    "thforce_clobber": {
        "asm": "thforce_clobber.asm",
        "expect": {
            "halt": 1,
            "x1": 0,
            "x10": 5,
            "x30": 7,
            "x31": "the_function",
        },
    },
    "thforce_nested": {
        "asm": "thforce_nested.asm",
        "expect": {
            "halt": 1,
            "x1": 0,
            "x10": 5,
            "x18": 0xABC,
            "x19": "nested_function",
            "x30": 5,
            "x31": "outer_function",
        },
    },
    "jalr_odd": {
        "asm": "jalr_odd.asm",
        "expect": {
            "halt": 1,
            "x10": 0x123,
            "pc": "done",
        },
    },
    "misaligned_load": {
        "asm": "misaligned_load.asm",
        "expect": {
            "halt": 1,
            "pc": 4,
            "x5": 0,
            "x30": 1001,
        },
    },
    "misaligned_store": {
        "asm": "misaligned_store.asm",
        "expect": {
            "halt": 1,
            "pc": 4,
            "x30": 1001,
        },
    },
    "jump_out_of_range": {
        "asm": "jump_out_of_range.asm",
        "expect": {
            "halt": 1,
            "pc": 0,
        },
    },
    "fall_through": {
        "asm": "fall_through.asm",
        "expect": {
            "halt": 1,
            "pc": 4,
        },
    },
}


def run_cmd(cmd, cwd=None, env=None):
    result = subprocess.run(
        cmd,
        cwd=cwd,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(map(str, cmd))}\n{result.stdout}")
    return result.stdout


def resolve_symbols(elf):
    output = subprocess.check_output([NM, str(elf)], text=True)
    symbols = {}
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 3:
            symbols[parts[2]] = int(parts[0], 16)
    return symbols


def parse_vcd(path):
    scopes = []
    names = {}
    values = {}
    with open(path) as f:
        for raw in f:
            line = raw.strip()
            if not line:
                continue
            if line.startswith("$scope"):
                scopes.append(line.split()[2])
            elif line.startswith("$upscope"):
                scopes.pop()
            elif line.startswith("$var"):
                parts = line.split()
                names[parts[3]] = ".".join(scopes + [parts[4]])
            elif line[0] == "#":
                continue
            elif line[0] in "01xzXZ":
                values[line[1:]] = line[0]
            elif line[0] == "b":
                parts = line.split()
                values[parts[1]] = parts[0][1:]

    result = {}
    for ident, full_name in names.items():
        raw = values.get(ident)
        if raw is None:
            continue
        if any(c in "xzXZ" for c in raw):
            result[full_name] = None
        else:
            result[full_name] = int(raw, 2)
    return result


def signal_name(key):
    if key == "pc":
        return "TOP.Core.pc"
    if key == "halt":
        return "TOP.Core.io_halt"
    if key.startswith("x") and key[1:].isdigit():
        number = int(key[1:])
        return None if number == 0 else f"TOP.Core.regFile.registers_{number - 1}"
    raise KeyError(key)


def resolve(value, symbols):
    return symbols[value] if isinstance(value, str) else value


def expected_value(values, key):
    name = signal_name(key)
    if name is None:
        return 0
    if name not in values:
        raise KeyError(f"missing VCD signal: {name}")
    return values[name]


def run_test(name, spec, keep):
    asm = TESTS_DIR / spec["asm"]
    base = GENERATED / f"test_{name}"
    obj = base.with_suffix(".o")
    elf = base.with_suffix(".elf")
    hex_file = base.with_suffix(".hex")
    obj_dir = GENERATED / f"test_{name}_obj"
    run_dir = GENERATED / f"test_{name}_run"

    for path in (obj_dir, run_dir):
        if path.exists():
            shutil.rmtree(path)
    run_dir.mkdir(parents=True)

    run_cmd([AS, "-march=rv32i", "-mabi=ilp32", str(asm), "-o", str(obj)])
    run_cmd([LD, "-T", str(ROOT / "link.ld"), str(obj), "-o", str(elf)])
    run_cmd([OBJCOPY, "-O", "verilog", "--verilog-data-width=4", str(elf), str(hex_file)])

    env = os.environ.copy()
    env["PROG"] = str(hex_file)
    run_cmd(["sbt", "-batch", "run"], cwd=ROOT, env=env)

    run_cmd(
        [
            VERILATOR,
            "--cc",
            "--exe",
            "--build",
            "--trace",
            "--quiet",
            "-f",
            "filelist.f",
            "../sim.cpp",
            "--top-module",
            "Core",
            "--Mdir",
            str(obj_dir),
        ],
        cwd=GENERATED,
    )

    sim_output = run_cmd([str(obj_dir / "VCore")], cwd=run_dir)
    symbols = resolve_symbols(elf)
    values = parse_vcd(run_dir / "dump.vcd")

    failures = []
    for key, expected in spec["expect"].items():
        want = resolve(expected, symbols)
        got = expected_value(values, key)
        if got != want:
            got_text = "X" if got is None else hex(got)
            failures.append(f"{key}: expected {hex(want)}, got {got_text}")

    match = re.search(r"Halted after (\d+) cycles", sim_output)
    cycles = match.group(1) if match else "?"

    if not keep and not failures:
        for path in (obj_dir, run_dir, obj, elf, hex_file):
            if path.is_dir():
                shutil.rmtree(path)
            elif path.exists():
                path.unlink()

    return failures, cycles


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true")
    parser.add_argument("tests", nargs="*")
    args = parser.parse_args()

    for tool in (AS, LD, OBJCOPY, NM, "sbt", VERILATOR):
        if shutil.which(tool) is None:
            print(f"missing required tool: {tool}", file=sys.stderr)
            return 1

    GENERATED.mkdir(exist_ok=True)

    names = args.tests or list(TESTS)
    failures = 0
    for name in names:
        if name not in TESTS:
            print(f"[{name}] FAIL")
            print(f"  unknown test: {name}")
            failures += 1
            continue

        print(f"[{name}] ", end="", flush=True)
        try:
            test_failures, cycles = run_test(name, TESTS[name], args.keep)
        except Exception as exc:
            print("FAIL")
            print(f"  {exc}")
            failures += 1
            continue

        if test_failures:
            print("FAIL")
            for message in test_failures:
                print(f"  {message}")
            failures += 1
        else:
            print(f"PASS ({cycles} cycles)")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
