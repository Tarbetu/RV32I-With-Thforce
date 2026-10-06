{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  riscv-toolchain = pkgsCross.riscv32-embedded;
in
mkShell {
  buildInputs = [
    sbt
    scala_2_13
    metals
    verilator
    surfer
    riscv-toolchain.buildPackages.binutils
  ];
}
