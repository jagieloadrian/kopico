# PoC Results — go/no-go gate (T013)

**Date**: 2026-07-03 (round 4 — CLOSED)

## Verdict: **FULL SUCCESS — CONFIRMED ON PHYSICAL HARDWARE**

**T013 executed on a physical Raspberry Pi Pico W**: after flashing
`kblink.uf2`, the LED showed 5 diagnostic blinks (boot/crt0 OK), followed
by continuous 250ms blinking driven from a loop in **Kotlin code**
(`Main.kt` → cinterop → CYW43). Kotlin/Native runs on bare-metal RP2040.

**Important hardware lesson (round 4)**: the first attempt on hardware
"didn't work" because the build was for `PICO_BOARD=pico`, while the
board is actually a **Pico W** — there the LED is NOT wired to GPIO25,
but to the CYW43 radio chip (WL_GPIO0, via `cyw43_arch_gpio_put`, library
`pico_cyw43_arch_none`). The binaries were probably working from the
start, just toggling an unconnected pin. Conclusion for the plugin:
distinguishing `_w` variants (FR-006) applies not just to cinterop WiFi,
but already to LED control itself — `BoardVariant.hasWifi` must affect
the default LED mechanism in examples/templates.

| Step | Status | Evidence |
|---|---|---|
| T007 — ARM toolchain + Pico SDK 2.2.0 | ✅ Success | `poc/SETUP.md` |
| T008 — Kotlin/Native 2.4.0 compiler | ✅ Success (after URL correction) | `poc/SETUP.md` |
| T009 — new custom target (name unrecognized) | ❌ Failure (workaround found below) | `poc/konan-target-spike.md` |
| T009b — retargeting via `-Xoverride-konan-properties` | ✅ Codegen works | `poc/konan-target-spike.md` § "Round 2" |
| T009c — **patching attributes in runtime `.bc` + C shim + lld + linker script** | ✅ **Full link works** | `poc/konan-target-spike.md` § "Round 3" |
| T010 — cinterop (`pico.klib` with GPIO wrappers) | ✅ Success (requires the same overrides as konanc) | `poc/interop/` |
| T011 — compiling blink Kotlin → ELF | ✅ Success — `kblink.elf`, 340 Kotlin functions, clean Thumb-1 (entry 0x100001e9) | `poc/blink/` |
| T012 — ELF → UF2 conversion | ✅ Success — `kblink.uf2` (544 KB), read correctly by `picotool info` | `poc/blink/build-k/` |
| T013 — flash to physical device and visual LED verification | ✅ **Success (2026-07-03)** — Pico W, 5 diagnostic blinks + 250ms blinking from Kotlin code | user confirmation; required rebuild for `PICO_BOARD=pico_w` (LED via CYW43) |

## Working pipeline (round 3)

```
Main.kt ──konanc──▶ libkotlinapp.a ──┐
  (-target linux_arm32_hfp           │
   -Xoverride-konan-properties=      ├──CMake+pico-sdk+lld──▶ kblink.elf ──picotool──▶ kblink.uf2
     cortex-m0plus/thumbv6m/static   │
   -Xbinary=gc=noop -Xallocator=std) │
pico.klib (cinterop, same           │
  overrides) ─────────────────────────┤
kopico_shim.c (~150 lines of stubs) ─┤
wrapper.c (GPIO bridge + main) ──────┘
```

Necessary components (all in `poc/blink/`):
1. **`.bc` attribute patch**: the runtime files in
   `konan/targets/linux_arm32_hfp/native/*.bc` have per-function
   attributes `target-cpu="arm1176jzf-s"`/`-thumb-mode` compiled in,
   which override the triple and generate ARM-mode code (illegal on
   Armv6-M). One-time rewrite: `clang -x ir → sed on attributes → clang
   -c -emit-llvm` (backup in `native.bak`). The same
   `-Xoverride-konan-properties` must also be given to `cinterop` (the
   klib bridge also carries ARM attributes).
2. **`staticLibraryRelocationMode=static`** in the overrides — without
   this the code is PIC and creates a `.got`, which the Pico linker
   script doesn't place into flash.
3. **C shim** (`kopico_shim.c`, ~150 lines): pthread no-op (single
   thread), mmap → static 64KB arena, `__aeabi_read_tp` (naked asm) +
   `__tls_get_addr`, `std::condition_variable` stubs (libstdc++
   arm-none-eabi is single-threaded), `stdout`/`stderr` as symbols (in
   newlib these are macros), `sleep`/`dladdr`/`syscall`.
4. **Linker: `ld.lld`** (from the LLVM K/N distribution) instead of
   `ld.bfd` — bfd can't digest Thumb relocations from LLVM objects; a
   3-line wrapper filters out the `--no-warn-rwx-segments` flag, which
   lld doesn't know.
5. **Linker script**: a copy of `memmap_default.ld` + `*(.got*)` into
   FLASH.

## Known limitations of the built artifact

- `gc=noop` — memory is allocated, never freed; fine for blink/simple
  apps, for long-lived applications requires investigating `gc=stms` +
  a thread shim.
- The runtime takes ~266KB flash (of 2MB) and ~72KB static RAM (of
  264KB) — acceptable, but size optimization is a separate topic
  (Phase 4 ROADMAP).
- The `.bc` patch modifies the compiler distribution — the plugin will
  need to do this automatically in its own cache (deterministic,
  one-time step).
- `linux_arm32_hfp` is deprecated — the K/N version is pinned by the
  constitution (2.4.0), so it's stable, but migrating to a newer K/N
  version requires re-validation.
- **Not tested on hardware** — static
  correctness (Thumb-1, ABI, memory layout) verified with tooling, but
  runtime init (K/N global constructors, stdlib initialization) could
  still surprise on a live chip.

## Round 2: `-Xoverride-konan-properties` — an important correction

The original conclusion ("impossible without forking the compiler") was
**too categorical**. The compiler has a documented flag
`-Xoverride-konan-properties`, which allows overriding configuration
properties (CPU, CPU features, triple, clang flags) for an **already
accepted** target name — there's no need to register a new name, it's
enough to "hijack" an existing one (`linux_arm32_hfp`).

**Empirically confirmed**: `konanc -target linux_arm32_hfp
-Xoverride-konan-properties="targetCpu.linux_arm32_hfp=cortex-m0plus;..."
-produce static` **succeeds** and produces a real `libhellostatic.a`
file with object code in `elf32-littlearm` format, EABI (not glibc) —
LLVM actually generated machine code for Cortex-M0+/Thumb-1, not just
accepted the flag without effect. Runtime.bc (LLVM bitcode, hence
portable IR) gets linked in correctly despite the triple mismatch
warning.

**However** — analysis of the undefined symbols in the resulting file
(134 symbols) reveals the real scale of the problem, different from
"the compiler can't do this":

| Category | Count | Feasibility assessment on RP2040 |
|---|---|---|
| `__aeabi_*` (libgcc) | 35 | ✅ Trivial — provided by `arm-none-eabi-gcc` itself |
| `pthread_*` (OS threads) | 21 | ❌ RP2040 has no OS-level threads — the K/N runtime (written in C++) assumes `std::thread`/`pthread_*` for its GC scheduler |
| libstdc++/C++ exceptions/RTTI (`_Z*`, `__cxa_*`, `_Unwind_*`) | 44 | ⚠️ To do, but embedded libstdc++ ports exist (e.g. partly in the Pico SDK) |
| `mmap`/`munmap` | 2 | ❌ **RP2040 has no MMU** — virtual memory mapping is physically impossible, not just unimplemented |

## Revised root cause

This is not (only) a problem of "a closed target list in the compiler" —
`-Xoverride-konan-properties` gets around that. The real, harder problem
lies **in the K/N runtime**: it's written in C++ and designed around a
multithreaded model with system threads (`pthread`) and an
`mmap`-based allocator (virtual memory). RP2040 (Cortex-M0+) has no
MMU — POSIX `mmap` is physically infeasible there, not merely missing.
Building a working runtime would require writing `pthread_*`
replacements (e.g. via a cooperative single-core scheduler or FreeRTOS)
and an `mmap`-free allocator (static memory pool) — this is a **runtime
port**, not a "fork the compiler to register a target", but still a
real, multi-week engineering project.

Ticket KT-44498 (`research.md` § 1) remains open, unassigned, and with
no planned version — official support is not on the way.

## Recommendation (after round 3)

Earlier recommendations (round 1: "requires a compiler fork", round 2:
"requires a multi-week runtime port") turned out to be too pessimistic.
Round 3 showed that the anticipated "runtime port" boiled down to
**~150 lines of C stubs + `.bc` attribute patch + lld + a linker script
fix** — completed in full within this spike, ending with a working UF2.
The history of rounds 1-2 above is kept deliberately as a record of the
process of arriving at the solution.

**T013 completed successfully (2026-07-03, Pico W)** — the go/no-go
gate is closed. **Phase 4 (US2 — plugin) is unblocked.** The plugin
tasks (`CompileNativeTask`, provisioning, `Uf2Writer`) have a complete,
hardware-verified technical recipe: konan.properties overrides, `.bc`
patch in the tool cache, shim/wrapper generation, lld + a flag-filtering
wrapper, a linker script with `.got` in FLASH, and LED routing via CYW43
for `_w` variants.
