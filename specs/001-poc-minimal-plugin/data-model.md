# Data Model: PoC & Minimalny Plugin Kotlin/Native dla Pico

Wyekstrahowane z `spec.md` (sekcja Key Entities) i doprecyzowane wymaganiami
funkcjonalnymi (FR-003 do FR-014).

## BoardVariant

Reprezentuje wspieraną płytkę docelową.

| Pole | Typ | Opis |
|---|---|---|
| `id` | `String` | jedna z: `"pico"`, `"pico_w"`, `"pico2"`, `"pico2_w"` (FR-003) |
| `chip` | enum `RP2040` \| `RP2350` | rodzina mikrokontrolera |
| `targetTriple` | `String` | `thumbv6m-none-eabi` (RP2040) lub `thumbv8m.main-none-eabi`/`eabihf` (RP2350) — FR-004 |
| `hasWifi` | `Boolean` | `true` dla `pico_w`/`pico2_w` — steruje FR-006 (cinterop CYW43) |

**Walidacja**: `id` spoza czterech dozwolonych wartości → błąd konfiguracji
(FR-008, edge case "nieprawidłowa wartość board").

**Reprezentacja**: `enum class BoardVariant` — zbiór jest zamknięty i znany w
czasie kompilacji pluginu (4 elementy), enum jest naturalnym, najprostszym
wyborem (brak potrzeby rozszerzalności w tej fazie).

## PicoSdkReference

Reprezentuje źródło Pico SDK używane do cinterop.

| Pole | Typ | Opis |
|---|---|---|
| `path` | `Provider<Directory>` | jawnie ustawiony przez `sdkPath` (opcjonalny — FR-003) lub wskazujący na cache po auto-provisioningu (FR-013) |
| `version` | `String` | odczytana z `pico_sdk_version.cmake` w SDK; musi być `>= 2.2.0` (FR-011) |
| `origin` | enum `USER_PROVIDED` \| `AUTO_PROVISIONED` | determinuje zachowanie walidacji: `USER_PROVIDED` → błąd przy niezgodności (FR-008); `AUTO_PROVISIONED` → plugin sam pobiera zgodną wersję (FR-013) |

**Reguła**: jeśli `origin == USER_PROVIDED` i `version < 2.2.0` (lub SDK
niekompletne/nieistniejące) → build kończy się czytelnym błędem; auto-
provisioning NIE nadpisuje jawnego wyboru dewelopera (edge case ze spec.md).

## ProvisionedTool

Reprezentuje pojedyncze narzędzie zewnętrzne dostarczane automatycznie przez
plugin (FR-013/FR-014): Pico SDK, toolchain ARM, `picotool`, OpenOCD.

| Pole | Typ | Opis |
|---|---|---|
| `name` | enum `PICO_SDK` \| `ARM_TOOLCHAIN` \| `PICOTOOL` \| `OPENOCD` \| `KOTLIN_NATIVE` | identyfikator narzędzia (K/N dodany po PoC — kompilator jest częścią provisionowanego toolchaina, `research.md` § 3; jego provisioning obejmuje post-instalacyjny patch atrybutów `.bc`) |
| `pinnedVersion` | `String` | przypięta wersja/tag do pobrania |
| `cacheDir` | `Directory` | `<gradleUserHome>/caches/kopico/<name>/<pinnedVersion>/` |
| `sourceUrl` | `String` | adres pobrania (release tarball / git remote) — patrz `research.md` dla konkretnych źródeł |
| `checksum` | `String?` | suma kontrolna do weryfikacji pobranego artefaktu (gdy dostępna u źródła) |

**Cykl życia**: `cacheDir` sprawdzany jako pierwszy (offline-first, SC-006);
brak trafienia w cache → pobranie z `sourceUrl`, weryfikacja `checksum` (jeśli
dostępna), zapis do `cacheDir`; błąd pobrania/weryfikacji → błąd konfiguracji
czytelny dla użytkownika (FR-008).

## BuildArtifact

Wynik builda gotowy do flashowania.

| Pole | Typ | Opis |
|---|---|---|
| `staticLib` | `RegularFile` | `libapp.a` z `konanc -produce static` (FR-001) — PoC wykazał, że konanc nie produkuje ELF bezpośrednio |
| `elfFile` | `RegularFile` | wynik `LinkTask` (link z pico-sdk + shim C + lld) |
| `uf2File` | `RegularFile` | wynik `GenerateUf2Task` — provisionowany `picotool uf2 convert` (FR-002, FR-007) |
| `boardVariant` | `BoardVariant` | płytka, dla której artefakt został zbudowany |

**Relacje**: `BuildArtifact` powstaje z `PicoSdkReference` + `BoardVariant` +
zestawu `ProvisionedTool` (kompilator K/N, toolchain ARM, picotool) przez
pipeline zadań Gradle (`CinteropTask` → `CompileNativeTask` → `LinkTask` →
`GenerateUf2Task`, patrz `plan.md` → Project Structure).
