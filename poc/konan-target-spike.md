# Spike: custom Kotlin/Native target for RP2040 (T009)

**Date**: 2026-07-03
**Compiler**: Kotlin/Native 2.4.0 (linux-x86_64 prebuilt,
`kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz`, downloaded directly from
GitHub Releases JetBrains/kotlin — see `research.md` § 3, URL fix)

## Steps

1. Checked the list of built-in targets: `konanc -list-targets`

   ```
   linux_x64 (default)
   linux_arm32_hfp (deprecated)
   linux_arm64
   mingw_x64
   android_x86
   android_x64
   android_arm32
   android_arm64
   ```

   No bare-metal/freestanding target whatsoever — consistent with the
   `research.md` § 1 finding (official list of supported targets).

2. Examined `konan/konan.properties` (796 lines) in the distribution. The
   file defines the **toolchain configuration** (paths to gcc/clang,
   sysroot, linker flags, CPU features) for keys in the format
   `<property>.<target_name>`, e.g. `targetTriple.linux_arm32_hfp`,
   `targetCpu.linux_arm32_hfp`. There is no mechanism there for declaring a
   *new* target name — the properties are only read for names the
   compiler already recognizes.

3. Attempt to compile with a nonexistent target:

   ```
   $ konanc -target thumbv6m-none-eabi hello.kt -o hello
   exception: java.lang.IllegalStateException: Unknown target: thumbv6m-none-eabi
       at org.jetbrains.kotlin.native.NativeFirstStageCompilationConfigKt.createFirstStageCompilationConfig(...)
   ```

   Same for `-target armv6-m`. The error comes from
   `NativeFirstStageCompilationConfig.kt` — target name validation happens
   against a closed `KonanTarget` enum compiled into the compiler JAR,
   **before** any consultation of `konan.properties`.

## Conclusion

**Extending `konan.properties` with a custom target does NOT WORK** with
the stock Kotlin/Native 2.4.0 compiler — this empirically refutes the
`research.md` § 1 hypothesis (community spike without confirmed success —
we now have direct evidence why: it's not a configuration issue, but a
hard limitation in the compiler code).

The only path to a custom bare-metal target would require:
- forking/patching the Kotlin/Native compiler (adding an entry to
  `KonanTarget`, recompiling `native/kotlin-native` from JetBrains/kotlin)
  — an enormous effort, out of scope for a "spike"; and
- writing/adapting the K/N runtime (GC, allocator, memory model) for an
  OS-less environment — the current runtime assumes `mmap`/threads/a
  hosted libc.

**Go/no-go gate verdict (see `poc/RESULTS.md`): FAILURE within the current
scope of the spike.** Requires escalation to the user before continuing
with T010-T013 (which depend on a working custom target) and before
Phase 4 (US2).

## Round 2 (2026-07-03): `-Xoverride-konan-properties` — an alternative without registering a new target name

Checked whether an intermediate LLVM/C artifact sidesteps the problem (it
doesn't — see `poc/RESULTS.md` option 2, `-produce bitcode` is explicitly
disabled in 2.4.0). While investigating this, discovered the flag
`-Xoverride-konan-properties=key=val;...` (`konanc -X`), which allows
overriding `konan.properties` properties from the CLI for an **existing,
accepted** target name — without needing to register a new name.

### Experiment: hijacking `linux_arm32_hfp` for Cortex-M0+

```bash
OVERRIDES="targetCpu.linux_arm32_hfp=cortex-m0plus;\
targetCpuFeatures.linux_arm32_hfp=+strict-align,-neon,-vfp2,-vfp3,-vfp4;\
targetTriple.linux_arm32_hfp=thumbv6m-none-eabi;\
clangFlags.linux_arm32_hfp=-cc1 -mfloat-abi soft -emit-obj -disable-llvm-optzns -x ir"

konanc -target linux_arm32_hfp \
  -Xoverride-konan-properties="$OVERRIDES" \
  -produce static hello.kt -o hellostatic
```

**Result**: success (with a warning about a triple mismatch between our
code's `thumbv6m-unknown-none-eabihf` and `runtime.bc`, which still
declares `armv6kz-unknown-linux-gnueabihf` — LLVM linked the modules
anyway, since `runtime.bc` is portable LLVM IR, not ready machine code).
The resulting `libhellostatic.a` contains an **actual ELF32
`elf32-littlearm` object, EABI** (not a glibc ABI) — verified via
`arm-none-eabi-readelf -h`.

An earlier attempt with `-produce dynamic` gets all the way to the
linking stage and only fails there (`ld.bfd: uses VFP register
arguments... does not` — a hard/soft-float mismatch between our code and
the glibc shared library for this target) — this confirms that **LLVM
codegen for Cortex-M0+/Thumb-1 actually works**; the problem is solely in
the final linking/runtime, not in the Kotlin-to-ARM-machine-code
translation itself.

### Analysis of the resulting object file

`arm-none-eabi-nm -u libhellostatic.a.o` → 134 undefined symbols:
35 `__aeabi_*` (libgcc, trivial), 21 `pthread_*`, 44
libstdc++/C++-exceptions/RTTI, 2 `mmap`/`munmap`. The K/N runtime (C++,
multithreaded GC) requires real OS threads and virtual memory mapping —
**RP2040 has no MMU**, so POSIX `mmap` is physically infeasible on this
chip, not merely missing.

### Revised conclusion

The thesis "the compiler must be forked" was too categorical —
`-Xoverride-konan-properties` gives working codegen without a fork. The
real barrier shifts from "the compiler can't do it" to "**the K/N runtime
needs a port**" to an environment without OS threads and without an MMU —
smaller, but still a multi-week project. Full analysis of options:
`poc/RESULTS.md`.

## Round 3 (2026-07-03): full Kotlin → UF2 pipeline — WORKS

Continuing round 2 led to a complete, working build. Problems encountered
and resolved, in order:

1. **`ld.bfd`: "Unknown destination type (ARM/Thumb)" / R_ARM_JUMP24**.
   The runtime files `konan/targets/linux_arm32_hfp/native/*.bc` have
   per-function LLVM attributes compiled in, `"target-cpu"="arm1176jzf-s"`,
   `"target-features"="...,-thumb-mode,..."`, which **override the
   triple** and generate ARM-mode code — illegal on Armv6-M (Thumb-only).
   Fix: a one-time patch of all `.bc` files (backup in `native.bak`):
   ```bash
   clang -target thumbv6m-unknown-none-eabi -x ir f.bc -S -emit-llvm -o - \
     | sed -e 's/"target-cpu"="arm1176jzf-s"/"target-cpu"="cortex-m0plus"/g' \
           -e 's/"target-features"="[^"]*"/"target-features"="+strict-align,+thumb-mode,+soft-float,-neon,..."/g' \
     | clang -target thumbv6m-unknown-none-eabi -x ir - -c -emit-llvm -o f.bc
   ```
   Note: `clang -x ir` without `-target` overrides the triple with the
   host's (x86_64) — it must be given explicitly. The same overrides must
   also be passed to `cinterop` (the bridge in the klib also carries ARM
   attributes).
2. **`ld.bfd` still can't digest Thumb relocations from LLVM objects** →
   link via `ld.lld` from the LLVM K/N distribution (`-fuse-ld=lld
   -B<dir>`); a 3-line wrapper filters out the `--no-warn-rwx-segments`
   flag (bfd-only).
3. **PIC → `.got` in RAM without an LMA** (picotool: "memory contents for
   uninitialized memory"). Fix:
   `staticLibraryRelocationMode.linux_arm32_hfp=static` in the overrides
   + a copy of `memmap_default.ld` with `*(.got*)` in the FLASH section
   (GOT entries resolved statically = read-only).
4. **`__aeabi_read_tp`** (local-exec TLS after switching to static) — a
   naked-asm stub returning a static block (special ABI: only r0 may be
   touched).
5. **`std::condition_variable`** — libstdc++ on arm-none-eabi is
   single-threaded and lacks these symbols; no-op stubs under the mangled
   names (valid C identifiers).
6. **`stdout`/`stderr`** — in newlib these are macros, not symbols; a
   separate TU defines globals and a constructor substitutes the real
   newlib streams.

**Result**: `kblink.elf` (340 Kotlin functions, Thumb entry point
0x100001e9, `kfun:#main` disassembles as pure Thumb-1) → `kblink.uf2`
(544 KB, `picotool info` reads it correctly, family rp2040, binary
0x10000000-0x100426d0). Complete reproduction recipe: `poc/blink/`
(CMakeLists.txt, wrapper.c, kopico_shim.c, kopico_stdio_globals.c,
memmap_kopico.ld, lld-wrap/).
