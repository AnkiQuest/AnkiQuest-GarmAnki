# SPDX-License-Identifier: AGPL-3.0-or-later
{
  description = "Garmanki Android companion: build environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

    # The dev shells, formatter and pinned toolchains of every AnkiQuest
    # repository.
    ankiquest-dev = {
      url = "github:AnkiQuest/AnkiQuest-Dev";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = {
    self,
    nixpkgs,
    ankiquest-dev,
  }: let
    forAllSystems = nixpkgs.lib.genAttrs ankiquest-dev.lib.systems;
  in {
    inherit (ankiquest-dev) formatter;

    # AnkiQuest-Dev's android shell: JBR 21 and the Android SDK with the
    # platforms and build tools of every AnkiQuest Android repository.
    # Run Gradle from android/: nix develop -c sh -c 'cd android && ./gradlew assembleDebug'
    devShells = forAllSystems (system: {
      default = ankiquest-dev.devShells.${system}.android;
    });

    checks = forAllSystems (system: {
      format = ankiquest-dev.lib.formatCheck nixpkgs.legacyPackages.${system} self;
    });
  };
}
