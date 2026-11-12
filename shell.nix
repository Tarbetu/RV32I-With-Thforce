{ pkgs ? import (fetchTarball "https://github.com/NixOS/nixpkgs/tarball/nixos-26.05") {} }:

with pkgs;

let
  riscv-toolchain = pkgsCross.riscv32-embedded;

  oss-cad-suite = stdenv.mkDerivation rec {
    pname = "oss-cad-suite";
    version = "2026-10-28";
    platform =
      let
        system = stdenv.buildPlatform.system;
      in
        if system == "x86_64-linux" then
          "linux-x64"
        else if system == "aarch64-linux" then
          "linux-arm64"
        else if system == "aarch64-darwin" then
          "darwin-arm64"
        else if system == "x86_64-windows" then
          "windows-x64"
        else
          abort "Unknown platform";

    src = fetchurl {
      url =
        "https://github.com/YosysHQ/oss-cad-suite-build/releases/download/${version}/oss-cad-suite-${platform}-${builtins.replaceStrings ["-"] [""] version}.tgz";
      hash =
        if platform == "linux-x64" then
          "sha256-XtBebhl8scmNB5d4pWD5TuBPe8X6CfIjm/MPAyZy5/I="
        else if platform == "linux-arm64" then
          "sha256-L9WFmoCkL/rAAyKvd9Z3KuhWOe5vjMzPgFxybl0llm8="
        else if platform == "darwin-arm64" then
          "sha256-HVc7wfvqWRAljUlOAI4wyLUDjl70GE2ryPD4cS5RwZY="
        else if platform == "windows-x64" then
          "sha256-65evnl0LGV5XDGp+49KcAFvMYOND1Iy3E+9BTko552Y="
        else
          abort;
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
    python3
    verilator
    surfer
    riscv-toolchain.buildPackages.binutils
    oss-cad-suite
  ];

  shellHook = ''
    export PATH=${oss-cad-suite}/bin:$PATH
  '';
}
