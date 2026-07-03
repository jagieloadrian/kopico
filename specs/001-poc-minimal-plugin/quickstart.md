# Quickstart: Validating the PoC & Minimal Plugin

This document describes how to manually verify that User Story 1 (PoC) and
User Story 2 (minimal plugin) from `spec.md` actually work end-to-end.

## Prerequisites

- Linux x86_64 (Assumptions in `spec.md`)
- A physical Raspberry Pi Pico device (for full validation of User Story 1;
  User Story 2 can be verified without physical hardware up to the
  flashing step)
- Network access on first run (auto-provisioning, FR-013)

## Scenario A: PoC validation (User Story 1)

1. Build a manually configured Kotlin/Native project with a custom target
   (see `research.md` § 1) and cinterop against `pico_stdlib`/`hardware_gpio`
   (see `research.md` § 2).
2. Compile the sample blink code (`gpio_put` on the LED pin).
   **Expected**: compilation succeeds, an ELF file is produced.
3. Generate a UF2 from the ELF file (`Uf2Writer`, `research.md` § 4).
   **Expected**: a valid `.uf2` file is produced.
4. Connect the Pico in BOOTSEL mode (hold the BOOTSEL button while
   plugging in USB) and copy the `.uf2` file onto the mounted `RPI-RP2`
   drive.
   **Expected**: the device automatically restarts and runs the program.
5. Observe the onboard LED.
   **Expected**: the LED blinks at the expected rate (SC-001).

## Scenario B: Minimal plugin validation (User Story 2)

1. Create a new, empty Gradle project.
2. Add to `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.anjo.kopico")
   }

   pico {
       board = "pico"
       // sdkPath intentionally left unset — testing auto-provisioning
   }
   ```
   (DSL contract: `contracts/extension-dsl.md`.)
3. Add the sample blink code from `examples/blink/src/nativeMain/kotlin/Main.kt`.
4. Run `./gradlew build` (first run — requires network access).
   **Expected**: the plugin automatically downloads and caches the Pico
   SDK (`>= 2.2.0`), the ARM toolchain, `picotool`, and OpenOCD (FR-013);
   the build succeeds; a `.uf2` file appears in the output directory
   (SC-002, within < 15 minutes).
5. Run `./gradlew build` again, this time **without network access** (e.g.
   `--offline` or with the network interface disconnected).
   **Expected**: the build succeeds using only the local cache (SC-006).
6. Change `board = "pico_w"` and run the build again.
   **Expected**: the plugin adds cinterop for CYW43 without any additional
   configuration (SC-003); the build succeeds.
7. Set `board = "invalid_board"` and run the build.
   **Expected**: the build fails with a readable configuration error
   indicating the allowed values (FR-008), not a raw stack trace.

## Acceptance criteria (reference)

Full list of measurable outcomes: `spec.md` → Success Criteria (SC-001
through SC-006).
