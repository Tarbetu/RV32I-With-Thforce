{ pkgs ? import <nixpkgs> {} }:

with pkgs;

mkShell {
  buildInputs = with pkgs; [
    sbt
    scala_2_13
    metals
    yosys
    verilator
  ];
}
