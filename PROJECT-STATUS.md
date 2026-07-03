# PROJECT STATUS — kopico

Last updated: 2026-07-03

## Completed

- **Project constitution** (`.specify/memory/constitution.md`, v1.2.3):
  Gradle 9.5.1 / Kotlin 2.4.0 (exact), no KMP, 100% Kotlin +
  kotlin-logging, tests exclusively in Kotest, ktlint+detekt, ponytail for
  implementation and verification, commits via git-committer.
- **ROADMAP.md**: plan for phases 0-5 (PoC → plugin → SDK integration → DX →
  advanced → release).
- **Spec + plan + tasks** for Phase 0/1: `specs/001-poc-minimal-plugin/`
  (spec.md after 3 rounds of clarify, plan.md with research.md, tasks.md with
  36 tasks, cross-artifact analysis clean — 100% requirements coverage).
- **Setup + Foundational (T001-T006)**: plugin scaffold
  (`java-gradle-plugin`, ktlint, detekt, Kotest, TestKit), `BoardVariant`
  enum + tests — build green.
- **PHASE 0 (PoC) COMPLETE — T007-T013**: blink written in Kotlin
  runs on physical Raspberry Pi Pico W. Pipeline: `konanc` with
  `linux_arm32_hfp`→cortex-m0plus retargeting → `libkotlinapp.a` →
  CMake+pico-sdk+lld → ELF → picotool → UF2. Recipe:
  `poc/konan-target-spike.md`; verdict: `poc/RESULTS.md`.
- **PHASE 1 (US2 + Polish) COMPLETE — T014-T036 (2026-07-03)**: the
  `com.anjo.kopico` plugin is fully functional. Extension DSL `pico { board,
  sdkPath }` with validation at configuration time; provisioners (Pico SDK,
  xPack GCC, picotool, OpenOCD, Kotlin/Native with `.bc` patching and a
  warm-up compile for clang dependencies); pipeline `kopicoCinterop →
  kopicoCompileNative → kopicoLink → kopicoUf2` wired into `build`.
  C resources from the PoC (`wrapper.c`, `kopico_shim.c`,
  `kopico_stdio_globals.c`, `memmap_kopico.ld`, CMake template) in
  `src/main/resources/kopico/`.
  28 Kotest tests + E2E TestKit (`KOPICO_E2E=1`): zero-config build →
  UF2 in 2m16s (SC-002 checked), `--offline` with cache checked (SC-006),
  `pico_w` with no extra configuration checked (SC-003). `examples/blink`
  builds as an external consumer from Maven Local (FR-009 checked). README
  (usage + "under the hood"), CI: `.github/workflows/build.yml` (SC-005).
  ktlint/detekt/tests green.

## Key decisions

1. **No KMP** — plain Kotlin/Native (Constitution Principle I); the source
   plan suggested KMP, deliberately rejected.
2. **Custom target via retargeting, not a compiler fork**: K/N 2.4.0 does
   not register new targets, but `-Xoverride-konan-properties` allows
   hijacking `linux_arm32_hfp` (cortex-m0plus/thumbv6m, static
   reloc). Requires a one-time patch of attributes in the runtime `.bc`
   (clang -x ir → sed → clang) — the plugin does this in its own cache.
3. **Runtime on bare metal via ~150 lines of C stubs** (pthread no-op,
   mmap = static arena, `__aeabi_read_tp` naked asm, cond_var stubs,
   stdout/stderr globals) + `-Xbinary=gc=noop -Xallocator=std`.
4. **Linking via `ld.lld`** (bfd can't digest Thumb relocations from LLVM) +
   linker script with `.got` in FLASH.
5. **Auto-provisioning of tools** (Pico SDK ≥2.2.0, xPack ARM GCC,
   picotool/OpenOCD from `pico-sdk-tools`) cached in the Gradle User Home;
   K/N compiler from GitHub Releases (download.jetbrains.com → 404).
6. **`_w` variants**: LED on CYW43 (not GPIO25) — the build must be
   per-board; `BoardVariant.hasWifi` also affects the LED, not just WiFi.
7. **gcc from PATH only at the pinned major version (15)**: the system
   arm-none-eabi-gcc 13 produces an ELF with a `.ram_vector_table` segment
   that has file content — picotool rejects it ("memory contents for
   uninitialized memory at 0x20000000"). FR-012 kept with a version gate.
8. **`Uf2Writer` in Kotlin dropped** in favor of the provisioned
   `picotool uf2 convert` (reuse instead of reimplementation; picotool
   is required anyway by FR-013).
9. **One klib from the `kopico.h` wrapper** instead of one klib per SDK
   library; per-variant SDK libraries are picked by
   `CinteropTask.sdkLibrariesFor` and linked by CMake. cinterop needs
   newlib header paths (queried via `gcc -E -Wp,-v`) and the latest lld from
   `~/.konan/dependencies`.

## Known PoC limitations

- `gc=noop` — no memory reclamation (for long-running applications,
  `gc=stms` + a thread shim needs investigation).
- Target `linux_arm32_hfp` deprecated (K/N version pinned, so stable).
- ~266KB flash / ~72KB RAM for the runtime — optimization is Phase 4.

## Next steps

1. **Merge `feature-1/init-project` → `main`** (feature 001 complete:
   36/36 tasks, all SC met).
2. **ROADMAP Phase 2/3**: deeper SDK integration (more Pico SDK libraries
   in cinterop), `flash`/`debug`/`monitor` tasks (OpenOCD already
   provisioned), stable public task names.
3. **RP2350 variants (`pico2`/`pico2_w`)**: the `.bc` patch and retargeting
   are currently pinned to cortex-m0plus/thumbv6m — extending to
   cortex-m33 requires a separate K/N distribution copy per chip (to be
   investigated during Phase 2); no RP2350 hardware verification yet.
4. Consider hardware verification of a plugin build (not just the PoC) —
   the UF2 from `examples/blink` is byte-similar to the
   hardware-confirmed `kblink.uf2`, but has not been flashed to a board.
