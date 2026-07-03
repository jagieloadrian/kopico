# Feature Specification: PoC & Minimal Kotlin/Native Plugin for Pico

**Feature Branch**: `001-poc-minimal-plugin`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "--phase 0,1" — reference to Phase 0 (Research & PoC)
and Phase 1 (Basic Gradle Plugin) from `ROADMAP.md`: confirming the feasibility
of writing Kotlin/Native code for the Raspberry Pi Pico and delivering a
minimal, working Gradle plugin `com.anjo.kopico`.

## Clarifications

### Session 2026-07-02

- Q: Should the plugin verify/enforce the Pico SDK version indicated by `sdkPath`? → A: Minimum SDK version (`>= 2.2.0`, the latest stable Pico SDK version at the time of this specification) — the plugin accepts newer versions and rejects older ones.
- Q: Should the plugin explicitly check for the availability of `arm-none-eabi-gcc` (and related tools) before building? → A: Yes — the plugin explicitly checks for the presence of the ARM toolchain before compilation and returns a readable error if it is missing.
- Q: Which tools should the plugin be able to download/provision on its own so a developer can use them without manual installation? → A: Pico SDK + ARM toolchain + picotool/OpenOCD (the full set, including flashing/debugging tools).
- Q: What should the plugin's tool download strategy be? → A: Automatic download with local cache and version pinning — downloads once into a cache directory, subsequent builds use the cache without network access.
- Q: Should SC-005 ("clean CI environment") require an actual, minimal CI workflow already at this stage, or is a local simulation of a clean environment sufficient? → A: A minimal, actual CI workflow is required (e.g. a single GitHub Actions job running the build) already at this stage — full CI/CD (multi-job, release automation, etc.) remains in Phase 5.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verifying feasibility of the approach (Priority: P1)

As an engineer building the plugin, I want a confirmed, working proof of
concept (PoC) showing that Kotlin/Native code can be compiled and run on a
Raspberry Pi Pico (RP2040) using the Pico SDK, before investing time in
building the full Gradle plugin.

**Why this priority**: Without confirming technical feasibility (custom
Kotlin/Native target + cinterop with the Pico SDK + UF2 generation), all
further plugin planning is risky — this is the foundation the entire project
stands on.

**Independent Test**: Can be verified independently by compiling a sample
"blink" program written in Kotlin/Native, generating a UF2 file from it, and
flashing it to a physical Pico device while observing the LED blink.

**Acceptance Scenarios**:

1. **Given** an installed Pico SDK and ARM toolchain, **When** a developer
   compiles a manually configured Kotlin/Native project with cinterop against
   `pico_stdlib`/`hardware_gpio`, **Then** compilation succeeds and an
   executable ELF file is produced.
2. **Given** a compiled ELF file, **When** a developer generates a UF2 from
   it, **Then** the UF2 file can be copied onto a Pico device in BOOTSEL mode.
3. **Given** a flashed UF2 with the blink program, **When** the Pico device is
   restarted, **Then** the onboard LED blinks at the expected rate.

---

### User Story 2 - Minimal plugin configuration for developers (Priority: P2)

As a developer who wants to write Kotlin code for the Pico, I want to install
the Gradle plugin `com.anjo.kopico`, configure the target board and the path
to the Pico SDK in it, and build a sample blink project without manually
writing cinterop or toolchain configuration.

**Why this priority**: This is the first real, publishable artifact of the
project — a minimal plugin that turns the manual PoC from User Story 1 into a
repeatable tool for other developers.

**Independent Test**: Can be tested independently by creating a new Gradle
project, adding the `com.anjo.kopico` plugin, declaring `board = "pico"` and
`sdkPath`, and running a build that produces a working UF2 file without any
additional manual cinterop configuration.

**Acceptance Scenarios**:

1. **Given** a new Gradle project with the `com.anjo.kopico` plugin applied,
   **When** a developer sets `board = "pico"` and `sdkPath` in the extension,
   **Then** the plugin automatically registers the appropriate Kotlin/Native
   target and configures cinterop for the key Pico SDK libraries
   (`pico_stdlib`, `hardware_gpio`, `hardware_pwm`).
2. **Given** a configured project with sample blink code in Kotlin, **When**
   a developer runs the plugin's build, **Then** the build succeeds and
   produces a UF2 file ready to be flashed onto the device.
3. **Given** a developer changes `board` to `"pico_w"`, **When** the build is
   run again, **Then** the plugin adds the additional cinterop for CYW43
   without requiring any other configuration change.
4. **Given** a developer has not set `sdkPath` and does not have the Pico
   SDK, ARM toolchain, picotool, or OpenOCD installed locally, **When** they
   run a build for the first time, **Then** the plugin automatically
   downloads and locally caches the required tools (in pinned versions)
   without user interaction, and the build succeeds.
5. **Given** the tools have already been downloaded once and are in the
   local cache, **When** a developer runs another build without network
   access, **Then** the build succeeds using only the cache.
6. **Given** a developer supplies an invalid `board` value (outside the
   supported set), **When** they run a build, **Then** they receive a
   readable error message pointing to the invalid configuration, instead of
   an unclear build failure.

---

### Edge Cases

- What happens when a developer explicitly sets `sdkPath`, but it points to
  a nonexistent, incomplete, or too old (< 2.2.0) copy of the Pico SDK?
  (Auto-provisioning does NOT override an explicitly provided, invalid path
  — the plugin reports a readable error instead of silently downloading a
  different version; auto-download only kicks in when `sdkPath` is not set.)
- How does the system behave when a developer supplies an unsupported
  `board` value (outside `pico`/`pico_w`/`pico2`/`pico2_w`)?
- What happens when an automatic tool download (Pico SDK, ARM toolchain,
  picotool, OpenOCD) fails (no network on first use, checksum mismatch,
  unavailable server)?
- How does the build behave when the PoC/plugin is run on a non-Linux host
  (outside the declared CI scope from `ROADMAP.md`) — auto-provisioning for
  other OSes is out of scope for this specification?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system (PoC) MUST demonstrate that code written in
  Kotlin/Native, compiled for a custom ARM target (without Kotlin
  Multiplatform), can call Pico SDK functions (e.g. `gpio_put`) via cinterop.
- **FR-002**: The system (PoC) MUST generate a UF2 file from the compiled
  Kotlin/Native program, capable of being flashed onto a physical Pico
  device.
- **FR-003**: The plugin MUST provide an extension DSL allowing a developer
  to declare `board` (one of: `pico`, `pico_w`, `pico2`, `pico2_w`) and
  optionally `sdkPath` — if `sdkPath` is not set, the plugin MUST
  automatically provision the Pico SDK per FR-013.
- **FR-004**: The plugin MUST automatically register the correct custom
  Kotlin/Native target (with the appropriate triple: `thumbv6m-none-eabi`
  for RP2040, `thumbv8m.main-none-eabi`/`eabihf` for RP2350) based on the
  selected `board`.
- **FR-005**: The plugin MUST automatically configure cinterop for the key
  Pico SDK libraries (`pico_stdlib`, `hardware_gpio`, `hardware_pwm`)
  without manual configuration by the user.
- **FR-006**: For `board` variants ending in `_w` (`pico_w`, `pico2_w`), the
  plugin MUST automatically add cinterop for `pico_cyw43_arch`
  (WiFi/CYW43); for variants without `_w`, this cinterop MUST be omitted.
- **FR-007**: The plugin MUST support building a sample "blink" project,
  resulting in a UF2 file ready to be flashed.
- **FR-008**: The plugin MUST return a readable, understandable
  configuration error when `board` is invalid, when an explicitly set
  `sdkPath` is nonexistent/incomplete/too old, or when automatic
  provisioning of a required tool (FR-013) fails — instead of an unreadable
  failure with a stack trace.
- **FR-011**: The plugin MUST ensure that the Pico SDK in use is at least
  version `2.2.0` (the latest stable Pico SDK version at the time of this
  specification): if `sdkPath` is explicitly set, the plugin validates its
  version and reports an error if it is older than required or cannot be
  read; if `sdkPath` is not set, the plugin automatically provisions an SDK
  version `>= 2.2.0` per FR-013.
- **FR-012**: The plugin MUST ensure the availability of an ARM toolchain
  (`arm-none-eabi-gcc` and related tools): if the toolchain is already
  available on PATH or in the plugin's local cache, the plugin uses it;
  otherwise it automatically provisions it per FR-013, instead of letting
  the missing toolchain surface as an unreadable compiler failure.
- **FR-009**: The plugin MUST be publishable locally (Maven Local), so it
  can be used in a separate sample test project.
- **FR-010**: The PoC documentation MUST describe the manual configuration
  process (step by step) underlying the automation provided by the plugin,
  so future contributors understand what the plugin does "under the hood".
- **FR-013**: The plugin MUST be able to automatically download and provide
  (without manual installation by the developer) the Pico SDK, ARM
  toolchain (`arm-none-eabi-gcc`), `picotool`, and OpenOCD — in versions
  satisfying the project's requirements (e.g. `>= 2.2.0` for the Pico SDK,
  per FR-011). `picotool`/OpenOCD are provided for future flash/debug tasks
  (Phase 3 of `ROADMAP.md`); within the scope of this specification (Phase
  0/1), only downloading and caching them is required, not implementing the
  `flash`/`debug` tasks themselves.
- **FR-014**: Automatic tool provisioning (FR-013) MUST use a local cache
  with version pinning: a network connection MUST be required only on the
  first download of a given tool/version on a given machine; subsequent
  builds MUST use the cache without network access.

### Key Entities

- **Board Variant**: represents a supported board (`pico`, `pico_w`,
  `pico2`, `pico2_w`); carries an assigned target triple and information on
  whether it supports WiFi/CYW43.
- **Pico SDK Reference**: the path/version of the local copy of the Pico SDK
  provided by the developer, from which the plugin draws headers for
  cinterop.
- **Build Artifact**: the resulting UF2 file (and intermediate ELF)
  produced by the build, ready for flashing onto the device.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The team can demonstrate a working blink program on a physical
  Pico device (LED blinking) based solely on Kotlin/Native code, within a
  maximum of 2 weeks from the start of work (per the Phase 0 estimate in
  `ROADMAP.md`).
- **SC-002**: A new developer, without a previously installed Pico SDK or
  ARM toolchain, is able to set up a project with the plugin from scratch
  and build a working UF2 for the `pico` board in under 15 minutes
  (including the time for the first automatic tool download), using only
  the README documentation.
- **SC-003**: Changing the target board from `pico` to `pico_w` (and vice
  versa) requires changing only a single line of configuration (`board =
  ...`) — with no additional manual steps.
- **SC-004**: 100% of the four supported board variants (pico, pico_w,
  pico2, pico2_w) have a correctly assigned target triple and cinterop
  logic, verifiable without physical access to all devices (e.g. through
  plugin configuration tests).
- **SC-005**: A build of the sample blink project succeeds (with no errors)
  on the first run in an actual, automated CI environment (a single hosted
  Linux job), without interactive intervention.
- **SC-006**: After the first download of the tools (Pico SDK, ARM
  toolchain, picotool, OpenOCD) on a given machine, subsequent builds of the
  same project succeed without network access, using only the local cache.

## Assumptions

- A developer using the plugin does NOT need to have the Pico SDK, ARM
  toolchain, `picotool`, or OpenOCD installed beforehand — the plugin
  provides them automatically (FR-013/FR-014). Manual installation remains
  possible (via an explicitly set `sdkPath` or tools available on PATH) and
  takes precedence over auto-provisioning.
- Auto-provisioning of tools requires network access only on the first
  download of a given tool/version on a given machine; download sources
  (URL/registry) and the exact cache location are a technical detail
  decided during the planning stage (`/speckit-plan`), not this
  specification.
- The development and CI environment is Linux — support for other
  operating systems is not required within this scope. Full CI/CD
  (multi-job, release automation, artifact publishing, etc.) remains the
  subject of Phase 5 of `ROADMAP.md`; **however, this specification does
  require a minimal, actual, single-job, automated CI environment already
  now** (SC-005) — verifying only the build success of the sample blink
  project on a clean Linux environment. The specific CI provider/service is
  a technical detail decided during the planning stage, not this
  specification.
- RP2350/the RISC-V core is out of scope for this specification (it is
  covered only from Phase 4 of `ROADMAP.md` onward) — at this stage RP2350
  is handled solely via its ARM core.
- "Key Pico SDK libraries" at this stage are limited to `pico_stdlib`,
  `hardware_gpio`, `hardware_pwm` — full SDK coverage is the subject of
  Phase 2.
- Flashing at this stage is done manually (copying the UF2 in BOOTSEL mode)
  — even though the plugin provides (downloads and caches) `picotool` and
  OpenOCD already at this stage (FR-013), an automated Gradle `flash` task
  using these tools is the subject of Phase 3.
- The minimum Pico SDK version (`2.2.0`) was determined based on the latest
  stable version available in the `raspberrypi/pico-sdk` repository at the
  time this specification was written (2026-07-02) — this value MUST be
  re-verified before implementation begins (Phase 0/1), in case a newer
  stable SDK release has since appeared.
