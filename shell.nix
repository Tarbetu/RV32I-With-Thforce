{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  verilator-compare = pkgs.writeShellApplication {
    name = "verilator-compare";
    runtimeInputs = [ verilator sbt git ];
    text = ''
         if ! root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
          echo "Error: You are not in a git repository." >&2
          exit 1
         fi

         cd "$root"

         if [ ! -f "build.sbt" ]; then
            echo "Error: No 'build.sbt' detected"
            exit1
         fi

         if [ ! -f "sim.cpp" ]; then
            echo "Error: No 'sim.cpp' detected"
            exit 1
         fi

         sbt run

         if [ ! -f "generated/filelist.f" ]; then
            echo "Error: No 'generated/filelist.f' found after build"
            exit 1
         fi

         cd generated
         echo "--------- V E R I L A T O R ---------"
         verilator --cc --exe --build -f filelist.f ../sim.cpp --top-module Core

         echo "--------- S I M U L A T I O N ---------"
         obj_dir/VCore
    '';
  };
in
mkShell {
  buildInputs = [
    sbt
    scala_2_13
    metals
    yosys
    verilator
    verilator-compare
  ];
}
