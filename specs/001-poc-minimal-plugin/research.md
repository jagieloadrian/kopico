# Research: PoC & Minimal Kotlin/Native Plugin for Pico

## 1. Feasibility of bare-metal ARM as a Kotlin/Native target (CRITICAL RISK)

**Decision**: Phase 0 (spike, timeboxed to 1-2 weeks per `ROADMAP.md`) is
explicitly treated as a **go/no-go gate** for the entire project, not a
formality. Approach: extend the Kotlin/Native target definition mechanism
(`konan.properties`/toolchain config) with a custom `armv6-m`
(`thumbv6m-none-eabi`) target using `picolibc` as the libc/runtime for an
OS-less environment, instead of waiting for official support.

**Rationale**: Verified from primary sources:
- The official list of supported Kotlin/Native targets
  (kotlinlang.org/docs/native-target-support.html) covers only hosted
  systems (macOS/iOS/Linux/Android/Windows/watchOS/tvOS) — **no bare-metal/
  freestanding target whatsoever**.
- The JetBrains ticket **KT-44498** ("Add RP2040 As A Kotlin Native
  Target") is **open, unassigned, with no planned version** — a plain
  backlog report, not a feature in progress.
- There is an active but **unfinished** community attempt (Raspberry Pi
  Forums, Kotlin Slack #compiler) to use a custom `armv6-m` target via
  `konan.properties` + `picolibc`. The Kotlin/Native runtime (GC,
  reflection, memory model) assumes a hosted libc/OS — contributors report
  unresolved blockers when building the K/N runtime itself for RP2040.
  **No evidence of a working end-to-end build producing a UF2** with the
  standard compiler, but also no evidence that it is definitively
  impossible.

**Alternatives considered**:
- Wait for official support via KT-44498 — rejected: no timeline, would
  block the entire project indefinitely.
- Drop Kotlin/Native in favor of C with a thin wrapper — rejected: breaks a
  fundamental assumption of the project (Constitution Principle I/II).
- Kotlin/Wasm or another backend — rejected: does not generate native ARM
  code runnable on a microcontroller without an OS.

**Implication for the plan**: If Phase 0 does not confirm feasibility
within the declared time (2 weeks, SC-001), an immediate escalation to the
user and a revision of `ROADMAP.md`/the constitution is required — the
plan assumes success, but does not explicitly guarantee it (hence the PoC
status, not "implementation").

## 2. Cinterop mechanism without KMP

**Decision**: The `cinterop` tool is distributed as a standalone
executable within the Kotlin/Native compiler distribution
(`<konan-dist>/bin/cinterop`), independent of the `kotlin("multiplatform")`
plugin. The plugin invokes it directly via Gradle `Exec`/`ExecOperations`,
with a `.def` file pointing to Pico SDK headers (`pico_stdlib`,
`hardware_gpio`, `hardware_pwm`, optionally `pico_cyw43_arch`).

**Rationale**: Low risk — this is a documented, stable compiler CLI
interface, also used internally by the KMP plugin.

**Alternatives considered**: Reimplementing cinterop — rejected,
unnecessary (ponytail ladder: use the existing tool).

## 3. Kotlin/Native compiler distribution

**Decision**: The plugin downloads the official Kotlin/Native 2.4.0
distribution for Linux x86_64 and caches it locally (FR-013/FR-014), then
invokes `bin/konanc` with custom target options (see point 1) via Gradle
`Exec`.
**Verified in Phase 0 (T008)**: `download.jetbrains.com` returns a 404 for
this distribution — the real, working URL is GitHub Releases:
`https://github.com/JetBrains/kotlin/releases/download/v2.4.0/kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz`
(plus a `.sha256` in the same release, for checksum verification).

**Rationale**: Consistent with FR-013 (auto-provisioning of the
"toolchain") — the K/N compiler itself is part of the toolchain needed to
build, even though the spec explicitly lists "Pico SDK, ARM toolchain,
picotool, OpenOCD"; the K/N compiler is the obvious, default element
without which FR-001 cannot be realized, so it is treated as a subset of
the "ARM toolchain" in the spirit of FR-013, not new scope.

**Alternatives considered**: Requiring the developer to manually install
Kotlin/Native — rejected, inconsistent with the auto-provisioning decision
(Clarifications in spec.md).

## 4. UF2 generation

**Decision (revised after the PoC, 2026-07-03)**: `GenerateUf2Task` calls
the provisioned `picotool uf2 convert <elf> <uf2> --family <rp2040|rp2350>`.
The original decision (a native `Uf2Writer.kt` in Kotlin) is superseded.

**Rationale**: The original premise ("elf2uf2 requires building from
source, no prebuilt binary") turned out to be outdated in practice during
the PoC: `picotool` — which the plugin provisions anyway (FR-013, prebuilt
from `pico-sdk-tools`) — has a built-in `uf2 convert` command, used
successfully in the PoC (verified on physical hardware). Ponytail ladder:
an existing, required dependency beats a custom implementation. Principle
II not violated — this is orchestration of an external tool (like
konanc/gcc), not plugin source code.

**Alternatives considered**: A native `Uf2Writer` in Kotlin — rejected as
an unnecessary reimplementation; it comes back on the table only if
picotool stops being provisioned or its `uf2 convert` proves insufficient.

## 5. Sources for tool auto-provisioning (FR-013/FR-014)

Verified from primary sources, with concrete URLs and formats:

| Tool | Source | Format | Notes |
|---|---|---|---|
| Pico SDK | `github.com/raspberrypi/pico-sdk`, pinned tag `2.2.0` | shallow git clone `--recurse-submodules` | the source tarball does not include submodules (e.g. `tinyusb`) — git clone is required |
| ARM toolchain (`arm-none-eabi-gcc`) | `github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases` | prebuilt tarball `xpack-arm-none-eabi-gcc-{VERSION}-linux-x64.tar.gz` + `.sha` checksum | ideal for automation — versioned, with checksums |
| `picotool` | `github.com/raspberrypi/pico-sdk-tools/releases` (NOT `raspberrypi/picotool` — that repo has only sources) | prebuilt `picotool-{VERSION}-*-x86_64-lin.tar.gz` | |
| OpenOCD (RP2040/RP2350 fork) | `github.com/raspberrypi/pico-sdk-tools/releases` (NOT `raspberrypi/openocd` — no Releases, only git tags) | prebuilt `openocd-{VERSION}-x86_64-lin.tar.gz` | |

**Decision**: All four tools are downloaded from the sources above, which
have been verified; `picotool` and OpenOCD share a single repo
(`pico-sdk-tools`), which simplifies the provisioning logic (one release
provider for both).

**Rationale**: Avoids the most common mistake in this area — the
`raspberrypi/picotool` and `raspberrypi/openocd` repos do NOT publish
prebuilt binaries (only sources/tags); the real location of the prebuilt
artifacts is `raspberrypi/pico-sdk-tools`.

**Alternatives considered**: Building `picotool`/OpenOCD from source via
CMake as part of auto-provisioning — rejected for this phase: it
significantly increases the time of the first build and the complexity
(requires CMake plus a host compiler as an additional dependency),
inconsistent with SC-002 (< 15 min for the first build). May come back as
a fallback if `pico-sdk-tools` does not publish a binary for a given
version/platform.

## Local cache (shared across all four tools)

**Decision**: `<gradleUserHome>/caches/kopico/<tool>/<version>/` — reuses
the existing Gradle User Home convention instead of inventing a custom
cache location.

**Rationale**: Consistent with Principle IV (Gradle ecosystem conventions)
and the ponytail ladder (reuse an existing mechanism instead of writing a
new one). Gradle User Home is already cleaned up/managed by existing tools
(`gradle --stop`, cache cleanup), so the plugin does not need to implement
its own cleanup logic at this stage.
