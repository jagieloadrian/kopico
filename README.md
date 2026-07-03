# kopico

Gradle plugin `com.anjo.kopico` — Kotlin/Native compilation for Raspberry Pi
Pico (RP2040/RP2350), from Kotlin code to a ready UF2 file, without manual
toolchain configuration.

Feasibility confirmed on physical hardware (Pico W, `poc/RESULTS.md`).

## Usage

```kotlin
plugins {
    id("com.anjo.kopico") version "0.1.0-SNAPSHOT"
}

pico {
    board = "pico"          // required: "pico" | "pico_w" | "pico2" | "pico2_w"
    // sdkPath = file("...") // optional — if omitted, the Pico SDK is downloaded automatically
}
```

```bash
./gradlew build
# output: build/kopico/<project-name>.uf2
```

Application code goes into `src/nativeMain/kotlin/`. The GPIO/LED API is
available via the `pico` package (cinterop with the `kopico.h` wrapper) — see
`examples/blink/`. Full DSL contract: `specs/001-poc-minimal-plugin/contracts/extension-dsl.md`,
validation scenarios: `specs/001-poc-minimal-plugin/quickstart.md`.

### Auto-provisioning (FR-013)

On the first build (network required) the plugin downloads and caches, in
`<gradleUserHome>/caches/kopico/<tool>/<version>/`:

| Tool | Version | Source |
|---|---|---|
| Pico SDK | 2.2.0 | shallow git clone `raspberrypi/pico-sdk` |
| ARM GCC | 15.2.1-1.1 | xPack `arm-none-eabi-gcc-xpack` (with SHA-256 verification) |
| Kotlin/Native | 2.4.0 | GitHub Releases `JetBrains/kotlin` (with SHA-256 verification) |
| picotool | 2.2.0-a4 | `raspberrypi/pico-sdk-tools` |
| OpenOCD | 0.12.0+dev | `raspberrypi/pico-sdk-tools` (for future flash/debug tasks) |

Tools already present on `PATH` (`arm-none-eabi-gcc`, `picotool`) take
precedence over downloading (FR-012). An explicitly set `sdkPath` is
validated (requires `>= 2.2.0`, FR-011) and is never overwritten.
Subsequent builds work offline (FR-014).

## How it works under the hood

Kotlin/Native does not officially support bare-metal ARM Cortex-M. The plugin
automates the recipe worked out and hardware-verified in the PoC
(`poc/konan-target-spike.md`, `poc/SETUP.md`):

1. **Retargeting instead of forking the compiler.** Registering a new target
   in `konan.properties` doesn't work (the target name is validated against a
   closed enum in the compiler). Instead, the plugin hijacks the existing
   `linux_arm32_hfp` target and overrides its codegen with the
   `-Xoverride-konan-properties` flag: `targetCpu=cortex-m0plus`,
   `targetTriple=thumbv6m-none-eabi`, soft-float, Thumb-only, static
   relocations (`KonanRetargeting.kt`).

2. **Patching runtime `.bc` attributes.** The files
   `konan/targets/linux_arm32_hfp/native/*.bc` have per-function LLVM
   attributes baked in (`target-cpu=arm1176jzf-s`, `-thumb-mode`), which
   override the triple and generate ARM-mode code — illegal on Armv6-M.
   `KotlinNativeProvisioner` performs a one-time patch
   (`clang -x ir → sed → clang -c -emit-llvm`, with a backup in `native.bak`,
   guarded by a marker against re-running).

3. **Task pipeline** (`kopicoCinterop → kopicoCompileNative → kopicoLink →
   kopicoUf2`, wired into `assemble`/`build`):
   - `cinterop` builds a klib from the `kopico.h` wrapper (the same
     overrides as konanc — the bridge in the klib also carries ARM
     attributes);
   - `konanc -produce static` with `-Xbinary=gc=noop -Xbinary=gcSchedulerType=manual
     -Xallocator=std` (RP2040 has no OS/MMU — no GC threads) produces
     `libkotlinapp.a`;
   - linking the final ELF is done by CMake with the Pico SDK (boot2, crt0,
     clocks) — the plugin injects its own resources: `wrapper.c` (GPIO/CYW43
     bridge + call into Kotlin's `main`), `kopico_shim.c` (pthread/mmap/TLS
     stubs — single-threaded environment without an MMU),
     `kopico_stdio_globals.c` (stdout/stderr as symbols for newlib), and
     `memmap_kopico.ld` (`.got` in FLASH); linking is done via `ld.lld` from
     the K/N dependencies, because `ld.bfd` can't digest the Thumb
     relocations from LLVM objects;
   - `picotool uf2 convert` turns the ELF into a UF2.

4. **Pico W / Pico 2 W:** the LED hangs off the CYW43 WiFi chip, not
   GPIO25. The wrapper routes LED operations through a pin sentinel to
   `cyw43_arch_gpio_put`, and the plugin links in `pico_cyw43_arch_none`
   only for the `_w` variants — the Kotlin code stays shared.

## Development

```bash
./gradlew test ktlintCheck detekt   # full verification
./gradlew publishToMavenLocal       # local publish for examples/
KOPICO_E2E=1 ./gradlew test         # full functional test (network, ~a dozen minutes)
./gradlew -p examples/blink build   # end-to-end example
```

Host requirements: Linux x86_64, JDK 21, `git`, `cmake`, `tar`.
