# Data Model: PoC & Minimal Kotlin/Native Plugin for Pico

Extracted from `spec.md` (Key Entities section) and refined by the
functional requirements (FR-003 through FR-014).

## BoardVariant

Represents a supported target board.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | one of: `"pico"`, `"pico_w"`, `"pico2"`, `"pico2_w"` (FR-003) |
| `chip` | enum `RP2040` \| `RP2350` | microcontroller family |
| `targetTriple` | `String` | `thumbv6m-none-eabi` (RP2040) or `thumbv8m.main-none-eabi`/`eabihf` (RP2350) — FR-004 |
| `hasWifi` | `Boolean` | `true` for `pico_w`/`pico2_w` — drives FR-006 (CYW43 cinterop) |

**Validation**: an `id` outside the four allowed values → configuration
error (FR-008, "invalid board value" edge case).

**Representation**: `enum class BoardVariant` — the set is closed and
known at plugin compile time (4 elements), an enum is the natural,
simplest choice (no need for extensibility at this stage).

## PicoSdkReference

Represents the Pico SDK source used for cinterop.

| Field | Type | Description |
|---|---|---|
| `path` | `Provider<Directory>` | explicitly set via `sdkPath` (optional — FR-003) or pointing to the cache after auto-provisioning (FR-013) |
| `version` | `String` | read from `pico_sdk_version.cmake` in the SDK; must be `>= 2.2.0` (FR-011) |
| `origin` | enum `USER_PROVIDED` \| `AUTO_PROVISIONED` | determines validation behavior: `USER_PROVIDED` → error on mismatch (FR-008); `AUTO_PROVISIONED` → the plugin downloads a matching version itself (FR-013) |

**Rule**: if `origin == USER_PROVIDED` and `version < 2.2.0` (or the SDK
is incomplete/nonexistent) → the build fails with a readable error;
auto-provisioning does NOT override the developer's explicit choice (edge
case from spec.md).

## ProvisionedTool

Represents a single external tool automatically provided by the plugin
(FR-013/FR-014): Pico SDK, ARM toolchain, `picotool`, OpenOCD.

| Field | Type | Description |
|---|---|---|
| `name` | enum `PICO_SDK` \| `ARM_TOOLCHAIN` \| `PICOTOOL` \| `OPENOCD` \| `KOTLIN_NATIVE` | tool identifier (K/N added after the PoC — the compiler is part of the provisioned toolchain, `research.md` § 3; its provisioning includes a post-install `.bc` attribute patch) |
| `pinnedVersion` | `String` | the pinned version/tag to download |
| `cacheDir` | `Directory` | `<gradleUserHome>/caches/kopico/<name>/<pinnedVersion>/` |
| `sourceUrl` | `String` | download address (release tarball / git remote) — see `research.md` for concrete sources |
| `checksum` | `String?` | checksum for verifying the downloaded artifact (when available at the source) |

**Lifecycle**: `cacheDir` is checked first (offline-first, SC-006); a
cache miss → download from `sourceUrl`, verify `checksum` (if available),
save to `cacheDir`; a download/verification failure → a configuration error
readable to the user (FR-008).

## BuildArtifact

The build output ready for flashing.

| Field | Type | Description |
|---|---|---|
| `staticLib` | `RegularFile` | `libapp.a` from `konanc -produce static` (FR-001) — the PoC showed that konanc does not directly produce an ELF |
| `elfFile` | `RegularFile` | the result of `LinkTask` (link against pico-sdk + C shim + lld) |
| `uf2File` | `RegularFile` | the result of `GenerateUf2Task` — provisioned `picotool uf2 convert` (FR-002, FR-007) |
| `boardVariant` | `BoardVariant` | the board the artifact was built for |

**Relations**: `BuildArtifact` is produced from `PicoSdkReference` +
`BoardVariant` + a set of `ProvisionedTool`s (K/N compiler, ARM toolchain,
picotool) through a Gradle task pipeline (`CinteropTask` →
`CompileNativeTask` → `LinkTask` → `GenerateUf2Task`, see `plan.md` →
Project Structure).
