# Contract: Plugin Extension DSL

Publiczny interfejs pluginu `com.anjo.kopico` widoczny dla deweloperów
stosujących plugin w swoim `build.gradle.kts` (FR-003, Zasada IV — Kotlin
DSL, `Provider`/`Property`, brak mutowalnych pól).

## `pico { }` extension

```kotlin
plugins {
    id("com.anjo.kopico") version "<wersja>"
}

pico {
    board = "pico"          // wymagane: "pico" | "pico_w" | "pico2" | "pico2_w"
    sdkPath = file("...")   // opcjonalne — brak = auto-provisioning (FR-013)
}
```

| Property | Typ | Wymagane | Domyślne | Walidacja |
|---|---|---|---|---|
| `board` | `Property<String>` | tak | brak (błąd, jeśli nieustawione lub spoza zbioru) | jedna z `pico`/`pico_w`/`pico2`/`pico2_w` (FR-003, FR-008) |
| `sdkPath` | `DirectoryProperty` | nie | auto-provisioning po cichej rezolucji cache (FR-013) | jeśli ustawione: musi istnieć, być kompletnym SDK, wersja `>= 2.2.0` (FR-011) |

## Błędy konfiguracji (kontrakt zachowania — FR-008)

Wszystkie błędy konfiguracji MUSZĄ być zgłaszane jako `GradleException` z
czytelnym komunikatem w języku naturalnym (nie surowy stack trace), w fazie
konfiguracji projektu (przed uruchomieniem zadań), obejmujące:

- nieprawidłowa/brakująca wartość `board`,
- jawnie ustawiony `sdkPath` wskazujący na nieistniejącą/niekompletną/zbyt
  starą kopię Pico SDK,
- niepowodzenie auto-provisioningu dowolnego narzędzia (FR-013: Pico SDK,
  toolchain ARM, `picotool`, OpenOCD) — brak sieci, niezgodna suma
  kontrolna, niedostępne źródło.

## Zadania Gradle udostępniane przez plugin (zakres tej fazy)

| Task | Wejście | Wyjście | Uwagi |
|---|---|---|---|
| (wewnętrzne, bez dedykowanej publicznej nazwy w tej fazie — patrz `tasks.md`) | kod źródłowy Kotlin/Native, `PicoSdkReference` | `BuildArtifact.elfFile` | cinterop + kompilacja (FR-001, FR-005, FR-006) |
| (wewnętrzne) | `BuildArtifact.elfFile` | `BuildArtifact.uf2File` | generowanie UF2 (FR-002, FR-007) |

Dokładne, stabilne nazwy zadań publicznych (`buildPico`, `buildUf2`, `flash`,
`debug`, `monitor` — patrz `ROADMAP.md` Faza 3) NIE są częścią kontraktu tej
fazy; ten dokument pokrywa wyłącznie zakres Fazy 0/1 (build → UF2, bez
flash/debug). Ostateczne nazewnictwo zadań ustala `/speckit-tasks`.
