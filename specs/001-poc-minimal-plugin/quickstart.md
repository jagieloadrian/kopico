# Quickstart: Walidacja PoC & Minimalnego Pluginu

Ten dokument opisuje jak ręcznie zweryfikować, że User Story 1 (PoC) i User
Story 2 (minimalny plugin) ze `spec.md` faktycznie działają end-to-end.

## Prerequisites

- Linux x86_64 (Assumptions w `spec.md`)
- Fizyczne urządzenie Raspberry Pi Pico (do pełnej walidacji User Story 1;
  User Story 2 da się zweryfikować bez fizycznego sprzętu aż do etapu
  flashowania)
- Dostęp do sieci przy pierwszym uruchomieniu (auto-provisioning, FR-013)

## Scenariusz A: Walidacja PoC (User Story 1)

1. Zbuduj ręcznie skonfigurowany projekt Kotlin/Native z custom targetem
   (patrz `research.md` § 1) i cinterop do `pico_stdlib`/`hardware_gpio`
   (patrz `research.md` § 2).
2. Skompiluj przykładowy kod blink (`gpio_put` na pinie diody LED).
   **Expected**: kompilacja kończy się sukcesem, powstaje plik ELF.
3. Wygeneruj UF2 z pliku ELF (`Uf2Writer`, `research.md` § 4).
   **Expected**: powstaje poprawny plik `.uf2`.
4. Podłącz Pico w trybie BOOTSEL (przytrzymaj przycisk BOOTSEL przy
   podłączaniu USB) i skopiuj plik `.uf2` na zamontowany dysk `RPI-RP2`.
   **Expected**: urządzenie automatycznie restartuje się i uruchamia
   program.
5. Obserwuj wbudowaną diodę LED.
   **Expected**: dioda mruga w oczekiwanym rytmie (SC-001).

## Scenariusz B: Walidacja minimalnego pluginu (User Story 2)

1. Utwórz nowy, pusty projekt Gradle.
2. Dodaj do `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.anjo.kopico")
   }

   pico {
       board = "pico"
       // sdkPath celowo nieustawione — test auto-provisioningu
   }
   ```
   (Kontrakt DSL: `contracts/extension-dsl.md`.)
3. Dodaj przykładowy kod blink z `examples/blink/src/nativeMain/kotlin/Main.kt`.
4. Uruchom `./gradlew build` (pierwsze uruchomienie — wymaga sieci).
   **Expected**: plugin automatycznie pobiera i cache'uje Pico SDK
   (`>= 2.2.0`), toolchain ARM, `picotool`, OpenOCD (FR-013); build kończy
   się sukcesem; w katalogu wyjściowym pojawia się plik `.uf2` (SC-002, w
   czasie < 15 minut).
5. Uruchom `./gradlew build` ponownie, tym razem **bez dostępu do sieci**
   (np. `--offline` lub odłączony interfejs sieciowy).
   **Expected**: build kończy się sukcesem, korzystając wyłącznie z
   lokalnego cache (SC-006).
6. Zmień `board = "pico_w"` i uruchom build ponownie.
   **Expected**: plugin dodaje cinterop dla CYW43 bez dodatkowej
   konfiguracji (SC-003); build kończy się sukcesem.
7. Ustaw `board = "invalid_board"` i uruchom build.
   **Expected**: build kończy się czytelnym błędem konfiguracji wskazującym
   dozwolone wartości (FR-008), nie surowym stack trace.

## Kryteria akceptacji (odwołanie)

Pełna lista mierzalnych wyników: `spec.md` → Success Criteria (SC-001 do
SC-006).
