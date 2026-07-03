<!--
Sync Impact Report
Version change: 1.2.2 → 1.2.3
Modified principles: none
Added sections: none
Removed sections: none
Modified sections:
  - I. Strict toolchain version control — Technical note updated after
    a successful T013: the retargeting technique confirmed on a physical
    Raspberry Pi Pico W (blink from Kotlin code works). Removed the caveat
    "validation in progress". Purely informational update — PATCH.
Follow-up TODOs: none
-->

<!--
LEGACY Sync Impact Report (v1.1.0 → v1.2.0, kept for history)
Version change: 1.1.0 → 1.2.0
Modified principles: none renamed
Added sections: none (existing section expanded)
Removed sections: none
Modified sections:
  - Domain scope: Raspberry Pi Pico Toolchain — added concrete domain
    requirements extracted from ROADMAP.md (target triples, minimal shape
    of the extension DSL, CYW43 cinterop for `_w` variants, Gradle tasks for
    the full build/UF2/flash/debug cycle, scope of future PIO/multicore/power
    management phases as not required early). Noted an explicit conflict: the
    source plan suggested Kotlin Multiplatform — rejected, Principle I (KMP
    ban) remains in force unchanged.
Added artifacts (outside constitution):
  - ROADMAP.md — the full phased plan (Phase 0-5) for the plugin
    implementation, adapted to a custom Kotlin/Native target instead of KMP.
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no changes needed (Constitution Check gate is generic, resolved at plan time)
  - .specify/templates/spec-template.md ✅ no changes needed (technology-agnostic by design)
  - .specify/templates/tasks-template.md ✅ no changes needed (generic task structure, adjusted per plan)
  - .specify/templates/checklist-template.md ✅ no changes needed
Follow-up TODOs: none
-->

# Kopico Constitution

## Core Principles

### I. Strict toolchain version control

The project MUST use exactly Gradle 9.5.1 and Kotlin 2.4.0 — no version
ranges (`+`, `latest.release`) or near-equivalent versions. The plugin MUST
operate in pure Kotlin/Native mode (custom native target or
`org.jetbrains.kotlin.platform.native`) — using Kotlin Multiplatform
(`kotlin("multiplatform")`) is forbidden. Any change to these versions
requires an explicit decision and an update to this constitution.

**Rationale**: The Pico SDK, cinterop, and UF2 generation are sensitive to
changes in the Kotlin/Native ABI and Gradle toolchain resolution; pinned
versions eliminate an entire class of "works on my machine" bugs and
incompatibilities between CI and local environments.

**Technical note (2026-07-03, confirmed on hardware)**:
Kotlin/Native 2.4.0 does not allow registering a *new named* target, but
the spike (`specs/001-poc-minimal-plugin/poc/`) confirmed — **including
validation on a physical Raspberry Pi Pico W** — a working workaround:
retargeting the existing `linux_arm32_hfp` target via
`-Xoverride-konan-properties` (cortex-m0plus/thumbv6m-none-eabi, static
reloc) + a one-time per-function attribute patch in the runtime `.bc` + a
layer of C stubs (pthread/mmap/TLS) + linking via `ld.lld`. The "custom
native target" in this principle is realized precisely through this
retargeting mechanism — full recipe: `poc/konan-target-spike.md`.

### II. 100% Kotlin, zero Java

All source code, build scripts, and plugin logic MUST be written in Kotlin
(Kotlin DSL for Gradle, no `.java` files). All logging in the plugin MUST go
through `io.github.oshai:kotlin-logging` — direct use of SLF4J, `println`
for state logging, or other logging libraries is forbidden.

**Rationale**: A single language and a single logging library simplify
maintenance, eliminate the need for Java/Kotlin interop bridging, and ensure
a consistent log format across the whole plugin.

### III. Test-First in Kotest (NON-NEGOTIABLE)

All tests (unit, integration, plugin tests via `TestKit`) MUST be written in
Kotest — using JUnit directly (`@Test` annotations from `org.junit`) is
forbidden, even though Kotest runs on the JUnit Platform engine under the
hood. New public functionality (Gradle tasks, DSL extensions, cinterop
wrappers) MUST have test coverage before merging.

**Rationale**: Kotest provides a readable DSL (`FunSpec`, `BehaviorSpec`),
property-based testing, and better assertions than JUnit; a single testing
framework prevents test-style fragmentation in a small project.

### IV. Gradle Plugin Best Practices

The plugin MUST be built following established patterns for writing Gradle
plugins in Kotlin: `java-gradle-plugin` for registration and publication,
convention plugins to share common configuration, and a clear separation
between the `extension` (public DSL) and internal `tasks`/`providers` logic.
The public DSL MUST be minimal and declarative — configuration via
`Provider`/`Property`, not mutable fields.

**Rationale**: Compliance with Gradle ecosystem conventions improves
configurability (lazy configuration, configuration cache) and lowers the
entry barrier for future contributors familiar with standard Gradle plugins.

### V. Quality and Static Analysis (NON-NEGOTIABLE)

Every code change MUST pass `ktlint` (formatting) and `detekt` (static
analysis) with no violations before committing. Verification (build, Kotest
tests, ktlint, detekt) MUST be run via the `ponytail` tool before every
commit. Commits and git operations (branch, commit, push) MUST be performed
exclusively by the `git-committer` agent, with messages following the
Conventional Commits convention.

**Rationale**: Hard quality gates (formatting + static analysis + tests) run
by a consistent mechanism before every commit prevent quality degradation in
a small/solo project, where there is no multi-stage code review.

## Domain scope: Raspberry Pi Pico Toolchain

The `com.anjo.kopico` plugin (group `com.anjo`, artifact `kopico`) MUST
enable writing Kotlin/Native code for Raspberry Pi Pico, Pico W, Pico 2, and
Pico 2 W (RP2040 and RP2350 microcontrollers). The functional scope covers:
configuring the Kotlin/Native toolchain for the target architecture, cinterop
with the Pico SDK, UF2 file generation, and flashing the device. Any new
domain functionality MUST explicitly declare which board variants
(RP2040/RP2350) it supports — lack of support for a variant MUST be
documented, not assumed by default. The detailed rollout schedule (phases,
deliverables, risks) is in `ROADMAP.md`; this section defines the durable
requirements arising from that plan, applicable regardless of phase.

Specific domain requirements:

- The plugin MUST support the target triples: `thumbv6m-none-eabi` for
  RP2040 and `thumbv8m.main-none-eabi` / `thumbv8m.main-none-eabihf` for
  RP2350, configured through a custom Kotlin/Native target (per Principle I —
  no KMP).
- The public extension DSL MUST expose at least a board selection
  (`board = "pico" | "pico_w" | "pico2" | "pico2_w"`) and a path to the
  Pico SDK (`sdkPath`).
- `_w` variants (Pico W, Pico 2 W) MUST automatically configure cinterop
  for `pico_cyw43_arch` (CYW43/WiFi) — variants without `_w` MUST skip this
  cinterop by default.
- The plugin MUST provide Gradle tasks covering the full cycle: building the
  binary, generating the UF2, flashing (via `picotool` or OpenOCD), and
  debugging (GDB + OpenOCD) — task names and exact scope are set by the
  phase plan per `ROADMAP.md`.
- Eventually (Phase 4+ in `ROADMAP.md`) the scope covers PIO, `pico_multicore`,
  and power-saving modes — these features are NOT required in the early
  phases and do not block earlier releases.

## Development workflow and quality gates

The `ponytail` tool MUST be used both for **implementation** and for
command execution/verification. This means the code itself (new classes,
Gradle tasks, DSL extensions, cinterop wrappers) MUST be written following
ponytail principles (YAGNI, reusing existing mechanisms before writing new
ones, stdlib/platform API before a dependency, the shortest working
implementation) — not merely run through it at the verification stage.
Build, tests, lint, and static analysis MUST be enforced and verified by
`ponytail`. Creating and managing commits (commit, branch, push) MUST be
done via the `git-committer` agent — direct `git commit`/`git push` calls
by any other executor are forbidden. Commit messages MUST follow
Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`,
`chore:`, etc.). Before every significant commit, full verification
(compilation, Kotest tests, ktlint, detekt) MUST be run via `ponytail`.

## Governance

This constitution takes precedence over any other practices, templates, and
documentation in the repository — in case of conflict, the constitution
wins. Amendments require: (1) an explicit description of the change and its
rationale, (2) a version number update per the semantic versioning rules
below, (3) propagation of changes to dependent templates
(`plan-template.md`, `spec-template.md`, `tasks-template.md`) if the rules
require it.

Constitution versioning follows semver:
- **MAJOR** — removal or backward-incompatible redefinition of a principle
  (e.g. changing the required Kotlin/Gradle version, allowing Java).
- **MINOR** — addition of a new principle or a substantial expansion of
  guidelines.
- **PATCH** — clarifications, editorial fixes, non-semantic changes.

Every plan (`plan.md`) and code review MUST include a Constitution Check
section verifying compliance with principles I–V above. Violating a
principle without a documented justification in `Complexity Tracking`
blocks merging.

**Version**: 1.2.3 | **Ratified**: 2026-07-02 | **Last Amended**: 2026-07-03
