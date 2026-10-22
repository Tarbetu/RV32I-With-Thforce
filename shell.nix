{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  riscv-toolchain = pkgsCross.riscv32-embedded;

  oss-cad-suite = stdenv.mkDerivation rec {
    pname = "oss-cad-suite";
    version = "20261021";

    src = fetchurl {
      url = "https://github.com/YosysHQ/oss-cad-suite-build/releases/download/${version}/oss-cad-suite-linux-x64-${version}.tgz";
      hash = "sha256-3enxcA3s4ZWpipsVhFpPQEOyqrHaz5vkmLutDDvh8kc=";
    };

    buildInputs = [
      stdenv.cc.cc.lib
      zlib
      glib
      libffi
      ncurses
      readline
      lz4
      libgcrypt
    ];

    nativeBuildInputs = [ autoPatchelfHook ];

    dontAutoPatchelf = false;

    installPhase = ''
      mkdir -p $out
      cp -r . $out/
    '';
  };
in
mkShell {
  buildInputs = [
    sbt
    scala_2_13
    metals
    verilator
    surfer
    riscv-toolchain.buildPackages.binutils
    oss-cad-suite
  ];

  shellHook = ''
    export PATH=${oss-cad-suite}/bin:$PATH
  '';
}
