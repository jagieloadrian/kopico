# Kopico Roadmap

Implementation plan for the Gradle plugin `com.anjo.kopico` for Kotlin/Native on
Raspberry Pi Pico / Pico 2 (RP2040 / RP2350).

> **STATUS: Phase 0 (PoC) COMPLETE — confirmed on physical hardware
> (2026-07-03).** Blink written in Kotlin runs on a physical Raspberry
> Pi Pico W (5 diagnostic flashes + 250ms blinking from a Kotlin loop).
> Technique: retargeting `linux_arm32_hfp` via
> `-Xoverride-konan-properties` (cortex-m0plus/thumbv6m, static reloc) +
> a one-time attribute patch in the runtime `.bc` + ~150 lines of C stubs
> (pthread/mmap/TLS) + linking via `ld.lld` + `.got` in FLASH. Hardware
> lesson: on `_w` variants the LED hangs off CYW43, not GPIO25 — the build
> must be per-board. Full recipe: `poc/konan-target-spike.md`;
> verdict and limitations (gc=noop, deprecated target): `poc/RESULTS.md`.
> **Phase 1 (minimal plugin) unblocked.**

> **Architectural note**: per Principle I of the project constitution
> (`.specify/memory/constitution.md`), the plugin operates in pure
> **Kotlin/Native** mode (custom native target), **not** Kotlin Multiplatform.
> All phases below assume configuration through a dedicated, custom
> Kotlin/Native target (the equivalent of a `KotlinNativeTarget` configured
> manually/by the plugin, without a `kotlin("multiplatform")` block), not the
> standard KMP preset mechanism.

## Scope

The plugin is meant to make it convenient to write code in Kotlin/Native (using the Pico SDK) for:

- **RP2040**: Pico + Pico W
- **RP2350**: Pico 2 + Pico 2 W

It integrates with the Pico SDK toolchain (CMake + `arm-none-eabi-gcc`/clang +
OpenOCD / picotool).

### Plugin goals

- Make it easy to declare the `pico`, `picoW`, `pico2`, `pico2W` targets.
- Automatic cinterop configuration with the Pico SDK.
- Support for the bootloader (UF2), flashing, debugging.
- Support for WiFi (CYW43) variants.
- Easy building and deployment (a Makefile-like experience in Gradle).
- Compatibility with existing Pico SDK projects (C/C++ interop).

## Phase 0: Research & PoC (1-2 weeks)

**Analysis of existing solutions**
- Check the JetBrains ticket: KT-44498 – adding RP2040 as a target.
- Analyze how custom targets work in Kotlin/Native (without KMP presets).
- Investigate existing cinterop with the Pico SDK (a header-only approach is possible).

**Technical PoC**
- Create a manual Kotlin/Native project with a custom target configured for
  the ARM Pico triple (without `kotlin("multiplatform")`).
- Configure cinterop for `pico-sdk` (headers from `pico-sdk/src/rp2_common`).
- Compile a simple blink in Kotlin (using `gpio_put` etc.).
- Generate a UF2 and verify on hardware.

**Tools**
- Install the Pico SDK + `arm-none-eabi-gcc`.
- Define the triples: `thumbv6m-none-eabi` (RP2040) and
  `thumbv8m.main-none-eabi` / `thumbv8m.main-none-eabihf` (RP2350).

**Deliverable**: A repository with a working PoC + documentation on "how it works manually".

## Phase 1: Basic Gradle Plugin (2-3 weeks)

**Goal**: A minimal working plugin.

**Tasks**:
- Create the Gradle plugin `com.anjo.kopico` using `java-gradle-plugin` +
  Kotlin DSL.
- Add an extension:
  ```kotlin
  pico {
      sdkPath = "/path/to/pico-sdk"
      board = "pico" // "pico_w" / "pico2" / "pico2_w"
      // other options: frequency, debug, etc.
  }
  ```
- Register the custom native target appropriate for the chosen board (without
  the KMP `kotlin { }` block with presets — the target is configured directly
  by the plugin for the correct triple).
- Support for W variants (additional cinterops for CYW43).
- Automatic cinterop configuration for the key Pico SDK libraries
  (`pico_stdlib`, `hardware_gpio`, `hardware_pwm`, etc.).

**Deliverable**: A publishable plugin (Maven Local / GitHub Packages) +
a sample blink project.

## Phase 2: Pico SDK and Build System Integration (3-4 weeks)

**Advanced cinterop**
- Automatic generation of `.def` files for the whole SDK or selected modules.
- Support for `pico-sdk` as a submodule or an external project.
- CMake integration handling (invoking CMake from Gradle via `exec` or a
  dedicated task).

**Binaries & Linking**
- Configuration of linker scripts (`memmap_default.ld`, etc.).
- UF2 generation (`elf2uf2` or a JVM/Kotlin port).
- Support for equivalents of `pico_add_library` / `pico_enable_stdio_usb`, etc.

**W versions**
- Dedicated `picoW` / `pico2W` targets.
- Automatic addition of `pico_cyw43_arch` and lwIP.

**Deliverable**: GPIO, UART, ADC, PWM, WiFi (on W) examples.

## Phase 3: Developer Experience & Tooling (2-3 weeks)

**Gradle tasks**:
- `buildPico` / `buildUf2`
- `flash` (via `picotool` or OpenOCD)
- `debug` (GDB + OpenOCD)
- `monitor` (minicom / picotool)

**Conventions and templates**:
- Convention plugins (e.g. `pico.kotlin`).
- Ready-made project templates (`gradle init`).

**Testing**:
- Unit tests on the host (Kotest, per Principle III) + integration on
  hardware (if possible).
- Hardware mocks (optional).

**Documentation**:
- README with examples.
- "Migrating from C SDK to Kotlin" guide.

**Deliverable**: A complete example application (e.g. USB CDC + LED + WiFi on Pico W).

## Phase 4: Advanced Features & Optimizations (2-4 weeks)

- Support for RP2350 (ARM; the RISC-V core is out of the initial scope — starting with ARM).
- 2nd stage bootloader.
- PIO (Programmable I/O) – bindings.
- Multicore (`pico_multicore`).
- Power management, sleep modes.
- Integration with `tinyusb`, `lwip`, `freertos` (optional).
- Binary size optimizations (`-Os`, stripping).
- Plugin publication to the Gradle Plugin Portal.

## Phase 5: Testing, Documentation, Release

- Testing on real hardware (all 4 variants: Pico, Pico W, Pico 2, Pico 2 W).
- Community examples (Blink, Hello World, Sensor, WiFi server).
- CI/CD (GitHub Actions – building on Linux).
- License (Apache 2.0 / MIT).
- Publication + announcement (Reddit, Raspberry Pi forums, Kotlin Slack).

## Risks / Challenges

- No official target support in Kotlin/Native → the custom target may be unstable.
- Changes in the Pico SDK (especially RP2350).
- Size and performance (Kotlin/Native has overhead relative to plain C).
- Debugging on bare metal.
