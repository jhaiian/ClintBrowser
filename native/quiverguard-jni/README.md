# quiverguard-jni

JNI bridge between Quiver Guard (ClintBrowser's ad blocker) and
[adblock-rust](https://github.com/brave/adblock-rust), pinned to `=0.13.0`.

Kotlin interacts with this library through
`com.jhaiian.clint.quiver.engine.QuiverGuardNative`; see that file and
`QuiverGuardEngine.kt` for the Kotlin-side API.

## Why this exists as a separate crate

`adblock-rust` performs all filter parsing, rule compilation, request matching,
cosmetic filtering, scriptlet injection, and redirect resource handling.
Everything on the Kotlin side (`QuiverGuardEngine`,
`QuiverGuardWebIntegration`, `QuiverGuardCompiler`, etc.) simply drives the
engine over JNI.

Because `adblock-rust` is a native Rust library, exposing it as a `cdylib`
provides the standard FFI boundary that the Android JVM can load through JNI.

## Building

Prebuilt native libraries are committed to
`app/src/main/jniLibs`, so building the Android app does **not** require Cargo,
Rust, or the Android NDK.

Whenever this crate changes, run the **Build native library** GitHub Actions
workflow manually. The workflow cross-compiles all supported Android ABIs and
updates the committed `.so` files in `app/src/main/jniLibs`.

This keeps normal Gradle builds fast since `./gradlew assemble` simply packages
the prebuilt native libraries instead of performing a Rust cross-compile every
time.

If you want to build locally before pushing, you'll need the Android NDK and
[cargo-ndk](https://github.com/bbqsrc/cargo-ndk):

```sh
cargo install cargo-ndk
rustup target add \
    aarch64-linux-android \
    armv7-linux-androideabi \
    i686-linux-android \
    x86_64-linux-android

cargo ndk \
    -t arm64-v8a \
    -t armeabi-v7a \
    -t x86 \
    -t x86_64 \
    -o ../../app/src/main/jniLibs \
    build --release
```

## Bundled resources (`src/bundled_resources.rs`)

`adblock-rust` does not ship uBlock Origin's redirect resources
(`$redirect=noopjs`, etc.) or scriptlets (`##+js(...)`). Instead, it expects
applications to register them with `Engine::use_resources()`.

`src/bundled_resources.rs` is generated directly from a uBlock Origin source
checkout using `tools/generate_resources.py`, which:

1. Executes `tools/extract-redirects.mjs` under Node to import uBO's
   `src/js/redirect-resources.js` registry and read every referenced redirect
   resource.
2. Executes `tools/extract-scriptlets.mjs` under Node to import uBO's
   `src/js/resources/scriptlets.js` module graph and serialize each registered
   scriptlet using `fn.toString()`, ensuring the generated source exactly
   matches upstream.
3. Generates `src/bundled_resources.rs` as a `Vec<Resource>`, using
   `MimeType::from_extension()` for redirect resources and the appropriate
   JavaScript MIME types for scriptlets and their dependencies.

To regenerate the bundled resources after updating uBlock Origin:

```sh
python3 tools/generate_resources.py /path/to/uBlock-Origin-checkout
```

The script only requires `node` on your `PATH` and a normal uBlock Origin source
checkout.

## Version pin

The `adblock` crate is pinned to exactly `0.13.0` rather than using a caret
version. The engine and resource APIs have changed between releases, so upgrades
should be performed deliberately and verified against `src/lib.rs` instead of
being picked up automatically.
