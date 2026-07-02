# Implementation Plan: PoC & Minimalny Plugin Kotlin/Native dla Pico

**Branch**: `001-poc-minimal-plugin` | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-poc-minimal-plugin/spec.md`

## Summary

Faza 0/1 z `ROADMAP.md`: udowodnić, że kod Kotlin/Native może działać na
Raspberry Pi Pico (RP2040) przez cinterop z Pico SDK i wygenerowanie UF2, a
następnie dostarczyć minimalny plugin Gradle `com.anjo.kopico` automatyzujący
to doświadczenie (extension DSL `board`/`sdkPath`, auto-rejestracja custom
Kotlin/Native targetu, auto-cinterop, auto-provisioning całego toolchaina —
Pico SDK, ARM GCC, `picotool`, OpenOCD — z lokalnym cache i przypięciem
wersji). Kluczowe ryzyko techniczne: Kotlin/Native oficjalnie nie wspiera
bare-metal ARM Cortex-M jako targetu — Faza 0 jest właśnie spike'iem
weryfikującym, czy da się to obejść (patrz `research.md`).

## Technical Context

**Language/Version**: Kotlin 2.4.0 (plugin: `kotlin("jvm")` na Gradle
9.5.1/JVM; generowany kod docelowy: Kotlin/Native custom target, bez KMP)

**Primary Dependencies**: `java-gradle-plugin`, `io.github.oshai:kotlin-logging`,
Kotest (`kotest-runner-junit5`, `kotest-assertions-core`), Gradle TestKit
(testy funkcjonalne pluginu)

**Storage**: N/A (baza danych nie dotyczy) — lokalny cache narzędzi na
dysku pod `<gradleUserHome>/caches/kopico/<narzędzie>/<wersja>/`

**Testing**: Kotest (`FunSpec`/`BehaviorSpec`) dla logiki jednostkowej +
Gradle TestKit dla testów funkcjonalnych pluginu (aplikacja pluginu do
projektu testowego, uruchomienie builda)

**Target Platform**: Plugin działa na JVM (Gradle daemon, Linux); kod
generowany przez plugin celuje w bare-metal ARM (RP2040 `thumbv6m-none-eabi`,
RP2350 `thumbv8m.main-none-eabi`/`eabihf`) bez systemu operacyjnego

**Project Type**: Gradle plugin (biblioteka) — pojedynczy moduł Gradle, plus
przykładowy projekt `examples/blink` konsumujący plugin

**Performance Goals**: SC-002 — pełna konfiguracja od zera do działającego
UF2 w < 15 minut (wliczając pierwsze pobranie narzędzi); poza tym build nie
jest wrażliwy na wydajność w tej fazie

**Constraints**: Gradle 9.5.1 i Kotlin 2.4.0 dokładnie (Zasada I); bez KMP
(Zasada I); środowisko dev/CI ograniczone do Linux x86_64 (Assumptions
spec.md); auto-provisioning musi działać offline po pierwszym pobraniu
(SC-006, FR-014)

**Scale/Scope**: 4 warianty płytek (pico, pico_w, pico2, pico2_w), 3
biblioteki Pico SDK w zakresie tej fazy (`pico_stdlib`, `hardware_gpio`,
`hardware_pwm`) + `pico_cyw43_arch` dla wariantów `_w`, jeden przykładowy
projekt blink

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Zasada | Ocena | Uzasadnienie |
|---|---|---|
| I. Ścisła kontrola wersji toolchainu | PASS | Plan trzyma Gradle 9.5.1 / Kotlin 2.4.0 dokładnie; custom Kotlin/Native target bez `kotlin("multiplatform")` (patrz research.md dla mechanizmu) |
| II. 100% Kotlin, zero Javy | PASS | Cały kod pluginu (w tym writer UF2 — patrz research.md decyzja) w Kotlinie; logowanie wyłącznie przez `kotlin-logging` |
| III. Test-First w Kotest | PASS | Testy jednostkowe i funkcjonalne (TestKit) w Kotest; brak JUnit-owych adnotacji bezpośrednio |
| IV. Best Practices Gradle Plugin | PASS | `java-gradle-plugin`, extension oparty o `Provider`/`Property`, zadania oddzielone od extension |
| V. Jakość i statyczna analiza | PASS (proceduralne) | ktlint/detekt jako bramki CI; `ponytail` używany do implementacji i weryfikacji; commity przez `git-committer` — egzekwowane w fazie wykonawczej, nie w architekturze |

Brak naruszeń — `Complexity Tracking` pozostaje pusty.

## Project Structure

### Documentation (this feature)

```text
specs/001-poc-minimal-plugin/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── extension-dsl.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/kotlin/com/anjo/kopico/
├── KopicoPlugin.kt              # java-gradle-plugin entry point
├── KopicoExtension.kt           # publiczny DSL: board, sdkPath (Provider/Property)
├── BoardVariant.kt              # enum/sealed: pico, pico_w, pico2, pico2_w + triple/CYW43 info
├── provisioning/
│   ├── PicoSdkProvisioner.kt    # klon/cache Pico SDK (FR-013/FR-014)
│   ├── ArmToolchainProvisioner.kt
│   ├── PicotoolProvisioner.kt
│   └── OpenOcdProvisioner.kt
├── tasks/
│   ├── CinteropTask.kt          # wywołanie `cinterop` z dystrybucji K/N
│   ├── CompileNativeTask.kt     # wywołanie `konanc` dla custom targetu
│   └── GenerateUf2Task.kt       # natywny writer formatu UF2 w Kotlinie
└── uf2/
    └── Uf2Writer.kt

src/test/kotlin/com/anjo/kopico/
├── BoardVariantTest.kt          # Kotest FunSpec
├── KopicoExtensionTest.kt
├── Uf2WriterTest.kt
└── KopicoPluginFunctionalTest.kt # Gradle TestKit

examples/blink/
├── build.gradle.kts             # zastosowanie pluginu, board = "pico"
└── src/nativeMain/kotlin/Main.kt # przykładowy kod blink (gpio_put)
```

**Structure Decision**: Pojedynczy moduł Gradle plugin (bez multi-module) —
zgodnie z Zasadą IV (`java-gradle-plugin` + convention najprościej realizuje
się jako jeden projekt na tym etapie). `examples/blink` to osobny,
niezależny projekt Gradle konsumujący plugin przez Maven Local (FR-009),
służący jako end-to-end test akceptacyjny User Story 1/2.

## Complexity Tracking

*Brak — Constitution Check przeszedł bez naruszeń.*
