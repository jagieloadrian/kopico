# Contract: Plugin Extension DSL

The public interface of the `com.anjo.kopico` plugin, visible to developers
applying the plugin in their `build.gradle.kts` (FR-003, Principle IV —
Kotlin DSL, `Provider`/`Property`, no mutable fields).

## `pico { }` extension

```kotlin
plugins {
    id("com.anjo.kopico") version "<version>"
}

pico {
    board = "pico"          // required: "pico" | "pico_w" | "pico2" | "pico2_w"
    sdkPath = file("...")   // optional — omitted = auto-provisioning (FR-013)
}
```

| Property | Type | Required | Default | Validation |
|---|---|---|---|---|
| `board` | `Property<String>` | yes | none (error if unset or outside the set) | one of `pico`/`pico_w`/`pico2`/`pico2_w` (FR-003, FR-008) |
| `sdkPath` | `DirectoryProperty` | no | auto-provisioning after silent cache resolution (FR-013) | if set: must exist, be a complete SDK, version `>= 2.2.0` (FR-011) |

## Configuration errors (behavior contract — FR-008)

All configuration errors MUST be reported as a `GradleException` with a
readable, natural-language message (not a raw stack trace), during the
project configuration phase (before tasks run), covering:

- an invalid/missing `board` value,
- an explicitly set `sdkPath` pointing to a nonexistent/incomplete/too old
  copy of the Pico SDK,
- a failure of auto-provisioning for any tool (FR-013: Pico SDK, ARM
  toolchain, `picotool`, OpenOCD) — no network access, checksum mismatch,
  unavailable source.

## Gradle tasks exposed by the plugin (scope of this phase)

| Task | Input | Output | Notes |
|---|---|---|---|
| (internal, no dedicated public name at this stage — see `tasks.md`) | Kotlin/Native source code, `PicoSdkReference` | `BuildArtifact.elfFile` | cinterop + compilation (FR-001, FR-005, FR-006) |
| (internal) | `BuildArtifact.elfFile` | `BuildArtifact.uf2File` | UF2 generation (FR-002, FR-007) |

The exact, stable names of the public tasks (`buildPico`, `buildUf2`,
`flash`, `debug`, `monitor` — see `ROADMAP.md` Phase 3) are NOT part of
this phase's contract; this document covers only the Phase 0/1 scope
(build → UF2, without flash/debug). The final task naming is decided by
`/speckit-tasks`.
