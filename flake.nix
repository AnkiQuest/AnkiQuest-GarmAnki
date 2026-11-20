{
  description = "Garmanki Android companion: build environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs = {nixpkgs, ...}: let
    system = "x86_64-linux";

    # The Android SDK is unfree; building with it means accepting its licence.
    pkgs = import nixpkgs {
      inherit system;
      config = {
        allowUnfree = true;
        android_sdk.accept_license = true;
      };
    };

    # What android/app/build.gradle.kts compiles against; build tools are AGP 9.5's default.
    compileSdk = "37";
    buildTools = "36.0.0";

    android = pkgs.androidenv.composeAndroidPackages {
      platformVersions = [compileSdk];
      buildToolsVersions = [buildTools];
      includeEmulator = false;
      includeSystemImages = false;
      includeNDK = false;
    };
    sdk = "${android.androidsdk}/libexec/android-sdk";
  in {
    formatter.${system} = pkgs.alejandra;

    # Run Gradle from android/: nix develop -c sh -c 'cd android && ./gradlew assembleDebug'
    devShells.${system}.default = pkgs.mkShell {
      packages = [pkgs.jdk17 android.androidsdk pkgs.alejandra];
      ANDROID_HOME = sdk;
      ANDROID_SDK_ROOT = sdk;
      # AGP's aapt2 from Maven is dynamically linked; use the SDK's, which Nix patched.
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildTools}/aapt2";
    };

    checks.${system}.format = pkgs.runCommand "alejandra-check" {} ''
      ${pkgs.alejandra}/bin/alejandra --check ${./flake.nix}
      touch $out
    '';
  };
}
