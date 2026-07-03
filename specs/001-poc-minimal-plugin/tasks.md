---

description: "Task list for PoC & Minimal Kotlin/Native Plugin for Pico"
---

# Tasks: PoC & Minimal Kotlin/Native Plugin for Pico

**Input**: Design documents from `/specs/001-poc-minimal-plugin/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/extension-dsl.md, quickstart.md

**Tests**: Kotest is mandatory per Constitution Principle III
(NON-NEGOTIABLE) — test tasks are therefore part of every phase, not
optional.

**Organization**: Tasks are grouped per user story (US1 = PoC, priority P1;
US2 = minimal plugin, priority P2) per `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can be executed in parallel (different files, no dependencies)
- **[Story]**: US1 or US2 — only for phase tasks belonging to a specific user story
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the Gradle plugin project

- [X] T001 Create the plugin project structure: root `build.gradle.kts`
      applying `java-gradle-plugin` + `kotlin("jvm")`, `settings.gradle.kts`,
      `gradle.properties` (`group=com.anjo`, `version=0.1.0-SNAPSHOT`) per
      `plan.md` → Project Structure
- [X] T002 [P] Configure ktlint and detekt in `build.gradle.kts` (Constitution
      Principle V)
- [X] T003 [P] Add dependencies in `build.gradle.kts`:
      `io.github.oshai:kotlin-logging`, Kotest (`kotest-runner-junit5`,
      `kotest-assertions-core`), Gradle TestKit
- [X] T004 [P] Create the skeleton of the standalone `examples/blink/`
      project: `examples/blink/settings.gradle.kts` (so it is buildable
      independently), an empty `examples/blink/build.gradle.kts`, a placeholder
      `examples/blink/src/nativeMain/kotlin/Main.kt` per `plan.md` →
      Project Structure

**Checkpoint**: The project builds (empty module), ktlint/detekt work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data model shared by the PoC (US1) and the plugin (US2)

**⚠️ CRITICAL**: Blocks both user stories.

- [X] T005 [P] Implement `enum class BoardVariant` (fields: `id`, `chip`,
      `targetTriple`, `hasWifi`) in
      `src/main/kotlin/com/anjo/kopico/BoardVariant.kt` per `data-model.md`
- [X] T006 [P] Kotest tests for `BoardVariant` (4 variants, correctness of
      `targetTriple`/`hasWifi`) in
      `src/test/kotlin/com/anjo/kopico/BoardVariantTest.kt`

**Checkpoint**: `BoardVariant` ready and tested — both user stories can
begin.

---

## Phase 3: User Story 1 - Verifying feasibility of the approach (Priority: P1) 🎯 MVP

**Goal**: Confirm (or refute) the feasibility of running Kotlin/Native code
on RP2040 through a manual, non-automated spike — a go/no-go gate for the
entire project (`research.md` § 1).

**Independent Test**: A manually compiled blink program in Kotlin/Native,
converted to UF2 and flashed onto a physical Pico device, causes the LED
to blink.

### Implementation for User Story 1

- [X] T007 [US1] Manually download the ARM toolchain (xPack
      `arm-none-eabi-gcc-xpack`, version pinned in `research.md` § 5) and the
      Pico SDK (`git clone --recurse-submodules` tag `2.2.0`) into
      `poc/toolchain/` and `poc/pico-sdk/`; document the steps in
      `poc/SETUP.md`
- [X] T008 [US1] Download the Kotlin/Native 2.4.0 compiler distribution
      (Linux x86_64) into `poc/kotlin-native/` per `research.md` § 3
- [X] T009 [US1] Attempt to define a custom Kotlin/Native target
      (`armv6-m`/`thumbv6m-none-eabi`) by extending `konan.properties` with
      `picolibc`; document the exact steps and blockers encountered in
      `poc/konan-target-spike.md` per `research.md` § 1 — **RESULT: FAILURE,
      empirically confirmed** (`-target` validates against a closed enum
      compiled into the compiler, before `konan.properties` is even read;
      see `poc/konan-target-spike.md`)
- [X] T010 [US1] Write a minimal `.def` file for cinterop in
      `poc/interop/pico_stdlib.def` and generate a klib via the `cinterop`
      CLI — **done** (klib with GPIO wrappers; requires the same
      `-Xoverride-konan-properties` as konanc, see
      `poc/konan-target-spike.md` § Round 3)
- [X] T011 [US1] Write minimal blink code in `poc/blink/Main.kt` and
      compile it to an ELF — **done** (via retargeting
      `linux_arm32_hfp`→cortex-m0plus + `.bc` attribute patch + C shim +
      lld + custom linker script; `kblink.elf`, 340 Kotlin functions, pure
      Thumb-1)
- [X] T012 [US1] Convert ELF → UF2 — **done** via `picotool uf2
      convert` (system picotool; a custom `Uf2FromElf.kt` in Kotlin turned
      out unnecessary at the PoC stage — the plugin's `Uf2Writer` will be
      built in T025/US2)
- [X] T013 [US1] Flash `kblink.uf2` onto a physical device and confirm
      the LED blinks — **done (2026-07-03) on a Pico W**: 5 diagnostic
      flashes + a continuous 250ms blink from Kotlin code. Required a
      rebuild under `PICO_BOARD=pico_w` (on the Pico W, the LED is on the
      CYW43 chip, not GPIO25 — the first attempt with a `pico` build waved
      a disconnected pin). Result recorded in `poc/RESULTS.md`

**Checkpoint**: ✅ **US1 COMPLETE — the go/no-go gate closed positively on
physical hardware.** Kotlin/Native runs on bare-metal RP2040 (Pico W).
Phase 4 (US2) unblocked; full technical recipe:
`poc/konan-target-spike.md`.

---

## Phase 4: User Story 2 - Minimal plugin configuration for developers (Priority: P2)

**Goal**: Deliver the `com.anjo.kopico` plugin automating the experience
from US1: extension DSL, auto-registration of the custom target,
auto-cinterop, auto-provisioning of the entire toolchain with a local
cache.

**Independent Test**: A new Gradle project with the plugin applied,
`board = "pico"`, with no `sdkPath` set, builds a working UF2 without
manual intervention (see `quickstart.md` Scenario B).

### Implementation for User Story 2

- [X] T014 [P] [US2] Implement `KopicoExtension` (`board: Property<String>`,
      `sdkPath: DirectoryProperty`) in
      `src/main/kotlin/com/anjo/kopico/KopicoExtension.kt` per
      `contracts/extension-dsl.md`
- [X] T015 [P] [US2] Kotest tests for `KopicoExtension` (validation of an
      invalid/missing `board`, default `sdkPath`) in
      `src/test/kotlin/com/anjo/kopico/KopicoExtensionTest.kt`
- [X] T016 [US2] Implement `KopicoPlugin` registering the `pico` extension
      in `src/main/kotlin/com/anjo/kopico/KopicoPlugin.kt` (depends on T014)
- [X] T017 [P] [US2] Implement `ToolCache`
      (`<gradleUserHome>/caches/kopico/<tool>/<version>/`) in
      `src/main/kotlin/com/anjo/kopico/provisioning/ToolCache.kt` per
      `research.md` → Local cache
- [X] T018 [P] [US2] Implement `PicoSdkProvisioner` (shallow git clone of
      the pinned tag, validation of version `>= 2.2.0`, distinguishing
      `USER_PROVIDED`/`AUTO_PROVISIONED`) in
      `src/main/kotlin/com/anjo/kopico/provisioning/PicoSdkProvisioner.kt`
      (FR-003, FR-011, FR-013)
- [X] T019 [P] [US2] Implement `ArmToolchainProvisioner` (check PATH before
      downloading, download + checksum + cache from xPack
      `arm-none-eabi-gcc-xpack` when not in PATH/cache) in
      `src/main/kotlin/com/anjo/kopico/provisioning/ArmToolchainProvisioner.kt`
      (FR-012, FR-013)
- [X] T020 [P] [US2] Implement `PicotoolProvisioner` (download + cache from
      `raspberrypi/pico-sdk-tools` releases) in
      `src/main/kotlin/com/anjo/kopico/provisioning/PicotoolProvisioner.kt`
      (FR-013)
- [X] T021 [P] [US2] Implement `OpenOcdProvisioner` (download + cache from
      `raspberrypi/pico-sdk-tools` releases) in
      `src/main/kotlin/com/anjo/kopico/provisioning/OpenOcdProvisioner.kt`
      (FR-013)
- [X] T022 [P] [US2] Implement `KotlinNativeProvisioner` (download the
      Kotlin/Native 2.4.0 distribution from `JetBrains/kotlin` GitHub
      Releases + `.sha256` verification + cache; **post-install step:
      patch per-function attributes in
      `konan/targets/linux_arm32_hfp/native/*.bc`** — `clang -x ir → sed
      target-cpu/target-features → clang -c -emit-llvm`, per
      `poc/konan-target-spike.md` § Round 3) in
      `src/main/kotlin/com/anjo/kopico/provisioning/KotlinNativeProvisioner.kt`
      (FR-013; research.md § 3 — the K/N compiler is part of the
      provisioned toolchain)
- [X] T023 [US2] Kotest tests for the provisioners in
      `src/test/kotlin/com/anjo/kopico/provisioning/ProvisionersTest.kt`,
      explicitly covering: cache hit/miss, checksum failure → readable
      configuration error, **rejection of an SDK version `< 2.2.0`**
      (FR-011), **use of a toolchain already available on PATH without
      downloading** (FR-012), and **idempotency of the `.bc` patch**
      (running it twice = the same result) (depends on T018-T022)
- [X] T024 [US2] Implement `CinteropTask` invoking the `cinterop` CLI for
      `pico_stdlib`/`hardware_gpio`/`hardware_pwm` (+ `pico_cyw43_arch` for
      `_w` variants) in
      `src/main/kotlin/com/anjo/kopico/tasks/CinteropTask.kt` — with the
      same `-Xoverride-konan-properties` as `CompileNativeTask` (the klib
      bridge carries the ARM attributes; per
      `poc/konan-target-spike.md` § Round 3)
      (FR-005, FR-006; depends on T016, T018, T022)
- [X] T025 [US2] Implement `CompileNativeTask` invoking `konanc` with
      retargeting based on `BoardVariant`: explicit flags per
      `poc/konan-target-spike.md` § Round 3 —
      `-Xoverride-konan-properties` (`targetCpu`, `targetCpuFeatures` from
      `+thumb-mode,+soft-float`, `targetTriple`,
      `staticLibraryRelocationMode=static`), `-Xbinary=gc=noop`,
      `-Xbinary=gcSchedulerType=manual`, `-Xallocator=std`,
      `-produce static` — in
      `src/main/kotlin/com/anjo/kopico/tasks/CompileNativeTask.kt` (FR-001,
      FR-004; depends on T024)
- [X] T026 [US2] Implement `LinkTask` — final linking of
      `libapp.a` with pico-sdk (boot2, crt0, clocks) into an ELF: providing
      `kopico_shim.c`, the GPIO/CYW43 wrapper, and the linker script
      (`.got` in FLASH) as plugin resources (`src/main/resources/kopico/`),
      orchestrating the link via `arm-none-eabi-g++` + `ld.lld` (a wrapper
      filtering bfd-only flags) per `poc/konan-target-spike.md` § Round 3
      and `poc/blink/CMakeLists.txt` — in
      `src/main/kotlin/com/anjo/kopico/tasks/LinkTask.kt` (FR-007; depends
      on T025, T019, T022)
- [X] T027 [US2] Implement `GenerateUf2Task` invoking the provisioned
      `picotool uf2 convert` on the ELF from `LinkTask` in
      `src/main/kotlin/com/anjo/kopico/tasks/GenerateUf2Task.kt` (FR-007;
      depends on T026, T020; a custom `Uf2Writer` unnecessary — picotool is
      already provisioned per FR-013, see `research.md` § 4)
- [X] T028 [US2] Wire the full task pipeline (Cinterop →
      CompileNative → Link → GenerateUf2) and configuration-phase
      validation/readable errors in `KopicoPlugin` in
      `src/main/kotlin/com/anjo/kopico/KopicoPlugin.kt` (FR-008; depends on
      T016, T024, T025, T026, T027)
- [X] T029 [US2] Fill in `examples/blink` (`build.gradle.kts` applying the
      plugin, blink code in
      `examples/blink/src/nativeMain/kotlin/Main.kt`) per `plan.md` and
      `quickstart.md` Scenario B. **Note (lesson from the PoC)**: on `_w`
      variants the LED is on the CYW43 chip, not GPIO25 — the example must
      handle both cases through routing in the wrapper layer (per
      `poc/blink/wrapper.c`), and the example's README must indicate the
      correct `board` for the owned device (depends on T028)
- [X] T030 [US2] Gradle TestKit functional test running a full build of
      `examples/blink` (first run with network access — timed with an
      assertion of `< 15 minutes` per SC-002, then an `--offline` rerun per
      SC-006)
      in
      `src/test/kotlin/com/anjo/kopico/KopicoPluginFunctionalTest.kt` per
      `quickstart.md` Scenario B (SC-002, SC-003, SC-006; depends on T029)
- [X] T031 [P] [US2] Kotest tests for `CinteropTask` asserting CYW43
      cinterop behavior for all 4 board variants (`pico`/`pico_w`
      presence/absence of `pico_cyw43_arch`, likewise `pico2`/`pico2_w`) in
      `src/test/kotlin/com/anjo/kopico/tasks/CinteropTaskTest.kt` (FR-006,
      SC-004; depends on T024)
- [X] T032 [US2] Publish the plugin locally
      (`./gradlew publishToMavenLocal`) and verify that `examples/blink`
      resolves it as an external plugin (FR-009; depends on T028)

**Checkpoint**: ✅ **US2 COMPLETE (2026-07-03)** — E2E TestKit: a new project
with `board = "pico"` and no `sdkPath` → UF2 in 2m16s (SC-002 ✓), an
`--offline` rerun with cache ✓ (SC-006), the `pico_w` variant builds
without additional configuration ✓ (SC-003). Deviations from the original
task description (forced empirically):
(1) T024 — cinterop builds a single klib from a `kopico.h` wrapper (as in
the PoC), not separate klibs per SDK library; the set of SDK libraries per
variant (including `pico_cyw43_arch_none` for `_w`) is determined by
`CinteropTask.sdkLibrariesFor` and linked by LinkTask; cinterop also
receives newlib header paths queried from
`arm-none-eabi-gcc -E -Wp,-v`. (2) T026 — the final link orchestrates
CMake with pico-sdk (generated from the `CMakeLists.txt` resource), not a
manual `arm-none-eabi-g++` call; lld is selected as the newest one from
`~/.konan/dependencies/llvm-*`.
(3) T019 — gcc from PATH is accepted only in the pinned major version
(15); system gcc 13 produces an ELF rejected by picotool
("memory contents for uninitialized memory at 0x20000000").
(4) T030 — the E2E test is gated behind the `KOPICO_E2E=1` variable
(network access + build minutes are not part of the default
`./gradlew test`; CI builds `examples/blink` directly per T035).

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, CI, and final quality verification

- [X] T033 [P] Write `README.md` describing plugin usage (extension DSL,
      auto-provisioning) referencing `contracts/extension-dsl.md` and
      `quickstart.md` (FR-010)
- [X] T034 [P] Document the manual configuration process (based on
      `poc/SETUP.md` and `poc/konan-target-spike.md`) as a "how it works
      under the hood" section in `README.md` (FR-010)
- [X] T035 [P] Create a minimal, single-job, automated CI workflow (e.g.
      `.github/workflows/build.yml`) building `examples/blink` on a clean
      Linux environment on every push (SC-005) — one job, without
      publishing artifacts/release automation (that remains Phase 5 of
      `ROADMAP.md`)
- [X] T036 Run full verification via `ponytail` (ktlint, detekt, the full
      Kotest test suite) for the whole module before considering Phase 0/1
      complete (Constitution Principle V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories
- **User Story 1 (Phase 3)**: Depends on Foundational; independent of US2
  (except that its results — T009, T011 — inform the implementation of
  T024 in US2)
- **User Story 2 (Phase 4)**: Depends on Foundational; T024 (custom target
  in the plugin) draws on the findings of T009/T011 from US1 — US1 should
  be complete (or at least T009 confirmed) before T024
- **Polish (Phase 5)**: Depends on completion of US1 and US2 (T035
  additionally depends on T029, since CI builds `examples/blink`)

### Parallel Opportunities

- Setup: T002, T003, T004 in parallel after T001
- Foundational: T005, T006 in parallel
- US1: sequential (T007→T008→T009→T010→T011→T012→T013) — each step
  depends on the environment state left by the previous one
- US2: T014/T015 in parallel; T017-T022 (provisioners + cache) in parallel
  after T016; T031 in parallel with T025+ after T024
- Polish: T033, T034, T035 in parallel

---

## Parallel Example: Foundational + US2 provisioning

```bash
# After T016 (KopicoPlugin ready), provisioners in parallel:
Task: "Implement PicoSdkProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/PicoSdkProvisioner.kt"
Task: "Implement ArmToolchainProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/ArmToolchainProvisioner.kt"
Task: "Implement PicotoolProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/PicotoolProvisioner.kt"
Task: "Implement OpenOcdProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/OpenOcdProvisioner.kt"
Task: "Implement KotlinNativeProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/KotlinNativeProvisioner.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (PoC) — **STOP and verify `poc/RESULTS.md`**
4. If the PoC confirms feasibility → continue to Phase 4 (US2). If not →
   escalate to the user before further work (see `plan.md` → Summary).

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 (PoC) → feasibility confirmed (go/no-go gate)
3. US2 (plugin) → first publishable artifact of the project
4. Polish → documentation, CI, and final quality verification

---

## Notes

- [P] = different files, no dependencies
- [Story] maps a task to a specific user story
- Kotest tests are mandatory (Principle III) — written before or alongside
  the implementation of the given component
- Every implementation task (T005+) is carried out following the
  `ponytail` principles (YAGNI, reuse, shortest working implementation) —
  not just the final verification in T036 (Constitution Principle V,
  v1.1.0)
- Principle II (100% Kotlin) applies to the source code and logic of the
  **plugin**. C files that are runtime resources for the target
  (shim/wrapper/linker script in `src/main/resources/kopico/` and their
  originals in `poc/blink/`) are artifacts injected into the user's build
  for bare-metal ARM — not plugin logic; C is unavoidable there (hardware
  bridging, ABI). The plugin's own helper/tooling scripts remain
  exclusively in Kotlin
- Commit after each task or logical group of tasks, delegating the commit
  to the `git-committer` agent (Principle V)
