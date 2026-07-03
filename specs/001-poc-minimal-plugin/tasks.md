---

description: "Task list for PoC & Minimalny Plugin Kotlin/Native dla Pico"
---

# Tasks: PoC & Minimalny Plugin Kotlin/Native dla Pico

**Input**: Design documents from `/specs/001-poc-minimal-plugin/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/extension-dsl.md, quickstart.md

**Tests**: Kotest jest obowiązkowy zgodnie z Zasadą III konstytucji
(NON-NEGOTIABLE) — zadania testowe są więc częścią każdej fazy, nie
opcjonalne.

**Organization**: Zadania pogrupowane per user story (US1 = PoC, priorytet
P1; US2 = minimalny plugin, priorytet P2) zgodnie ze `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Można wykonać równolegle (różne pliki, brak zależności)
- **[Story]**: US1 lub US2 — tylko dla zadań fazowych per user story
- Każde zadanie zawiera dokładną ścieżkę pliku

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicjalizacja projektu Gradle plugin

- [X] T001 Utwórz strukturę projektu pluginu: root `build.gradle.kts`
      stosujący `java-gradle-plugin` + `kotlin("jvm")`, `settings.gradle.kts`,
      `gradle.properties` (`group=com.anjo`, `version=0.1.0-SNAPSHOT`) per
      `plan.md` → Project Structure
- [X] T002 [P] Skonfiguruj ktlint i detekt w `build.gradle.kts` (Zasada V
      konstytucji)
- [X] T003 [P] Dodaj zależności w `build.gradle.kts`:
      `io.github.oshai:kotlin-logging`, Kotest (`kotest-runner-junit5`,
      `kotest-assertions-core`), Gradle TestKit
- [X] T004 [P] Utwórz szkielet samodzielnego projektu `examples/blink/`:
      `examples/blink/settings.gradle.kts` (tak by był buildowalny
      niezależnie), pusty `examples/blink/build.gradle.kts`, placeholder
      `examples/blink/src/nativeMain/kotlin/Main.kt` per `plan.md` →
      Project Structure

**Checkpoint**: Projekt się buduje (pusty moduł), ktlint/detekt działają.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Model danych współdzielony przez PoC (US1) i plugin (US2)

**⚠️ CRITICAL**: Blokuje obie user stories.

- [X] T005 [P] Zaimplementuj `enum class BoardVariant` (pola: `id`, `chip`,
      `targetTriple`, `hasWifi`) w
      `src/main/kotlin/com/anjo/kopico/BoardVariant.kt` per `data-model.md`
- [X] T006 [P] Testy Kotest dla `BoardVariant` (4 warianty, poprawność
      `targetTriple`/`hasWifi`) w
      `src/test/kotlin/com/anjo/kopico/BoardVariantTest.kt`

**Checkpoint**: `BoardVariant` gotowy i przetestowany — obie user stories
mogą się rozpocząć.

---

## Phase 3: User Story 1 - Zweryfikowanie wykonalności podejścia (Priority: P1) 🎯 MVP

**Goal**: Potwierdzić (lub obalić) wykonalność uruchomienia kodu
Kotlin/Native na RP2040 przez ręczny, niezautomatyzowany spike — bramka
go/no-go dla całego projektu (`research.md` § 1).

**Independent Test**: Skompilowany ręcznie program blink w Kotlin/Native,
przekonwertowany na UF2 i wgrany na fizyczne urządzenie Pico, powoduje
mruganie diody LED.

### Implementation for User Story 1

- [X] T007 [US1] Pobierz ręcznie toolchain ARM (xPack
      `arm-none-eabi-gcc-xpack`, wersja pinowana w `research.md` § 5) i Pico
      SDK (`git clone --recurse-submodules` tag `2.2.0`) do `poc/toolchain/`
      i `poc/pico-sdk/`; udokumentuj kroki w `poc/SETUP.md`
- [X] T008 [US1] Pobierz dystrybucję kompilatora Kotlin/Native 2.4.0
      (Linux x86_64) do `poc/kotlin-native/` per `research.md` § 3
- [X] T009 [US1] Podejmij próbę zdefiniowania custom targetu Kotlin/Native
      (`armv6-m`/`thumbv6m-none-eabi`) przez rozszerzenie
      `konan.properties` z użyciem `picolibc`; udokumentuj dokładne kroki i
      napotkane blokery w `poc/konan-target-spike.md` per `research.md` § 1
      — **WYNIK: PORAŻKA, empirycznie potwierdzona** (`-target` waliduje
      względem zamkniętego enuma wkompilowanego w kompilator, przed
      odczytem `konan.properties`; patrz `poc/konan-target-spike.md`)
- [X] T010 [US1] Napisz minimalny plik `.def` dla cinterop w
      `poc/interop/pico_stdlib.def` i wygeneruj klib przez CLI `cinterop` —
      **wykonane** (klib z wrapperami GPIO; wymaga tych samych
      `-Xoverride-konan-properties` co konanc, patrz
      `poc/konan-target-spike.md` § Runda 3)
- [X] T011 [US1] Napisz minimalny kod blink w `poc/blink/Main.kt` i
      skompiluj do ELF — **wykonane** (przez retargeting
      `linux_arm32_hfp`→cortex-m0plus + patch atrybutów `.bc` + shim C +
      lld + custom linker script; `kblink.elf`, 340 funkcji Kotlin, czysty
      Thumb-1)
- [X] T012 [US1] Konwersja ELF → UF2 — **wykonane** przez `picotool uf2
      convert` (systemowy picotool; własny `Uf2FromElf.kt` w Kotlinie
      zbędny na etapie PoC — `Uf2Writer` pluginu powstanie w T025/US2)
- [X] T013 [US1] Wgraj `kblink.uf2` na fizyczne urządzenie i potwierdź
      mruganie diody — **wykonane (2026-07-03) na Pico W**: 5 błysków
      diagnostycznych + ciągłe mruganie 250ms z kodu Kotlin. Wymagało
      rebuildu pod `PICO_BOARD=pico_w` (na Pico W dioda jest na chipie
      CYW43, nie GPIO25 — pierwsza próba z buildem `pico` machała
      niepodłączonym pinem). Wynik zapisany w `poc/RESULTS.md`

**Checkpoint**: ✅ **US1 ZAKOŃCZONE — bramka go/no-go domknięta pozytywnie
na fizycznym sprzęcie.** Kotlin/Native działa na bare-metal RP2040 (Pico W).
Faza 4 (US2) odblokowana; pełny przepis techniczny:
`poc/konan-target-spike.md`.

---

## Phase 4: User Story 2 - Minimalna konfiguracja pluginu przez deweloperów (Priority: P2)

**Goal**: Dostarczyć plugin `com.anjo.kopico` automatyzujący doświadczenie z
US1: extension DSL, auto-rejestracja custom targetu, auto-cinterop,
auto-provisioning całego toolchaina z lokalnym cache.

**Independent Test**: Nowy projekt Gradle z zastosowanym pluginem,
`board = "pico"`, bez ustawionego `sdkPath`, buduje się do działającego UF2
bez ręcznej interwencji (patrz `quickstart.md` Scenariusz B).

### Implementation for User Story 2

- [ ] T014 [P] [US2] Zaimplementuj `KopicoExtension` (`board: Property<String>`,
      `sdkPath: DirectoryProperty`) w
      `src/main/kotlin/com/anjo/kopico/KopicoExtension.kt` per
      `contracts/extension-dsl.md`
- [ ] T015 [P] [US2] Testy Kotest dla `KopicoExtension` (walidacja
      nieprawidłowego/brakującego `board`, domyślny `sdkPath`) w
      `src/test/kotlin/com/anjo/kopico/KopicoExtensionTest.kt`
- [ ] T016 [US2] Zaimplementuj `KopicoPlugin` rejestrujący extension `pico`
      w `src/main/kotlin/com/anjo/kopico/KopicoPlugin.kt` (zależy od T014)
- [ ] T017 [P] [US2] Zaimplementuj `ToolCache`
      (`<gradleUserHome>/caches/kopico/<narzędzie>/<wersja>/`) w
      `src/main/kotlin/com/anjo/kopico/provisioning/ToolCache.kt` per
      `research.md` → Cache lokalny
- [ ] T018 [P] [US2] Zaimplementuj `PicoSdkProvisioner` (shallow git clone
      pinowanego tagu, walidacja wersji `>= 2.2.0`, rozróżnienie
      `USER_PROVIDED`/`AUTO_PROVISIONED`) w
      `src/main/kotlin/com/anjo/kopico/provisioning/PicoSdkProvisioner.kt`
      (FR-003, FR-011, FR-013)
- [ ] T019 [P] [US2] Zaimplementuj `ArmToolchainProvisioner` (sprawdzenie
      PATH przed pobraniem, pobranie + checksum + cache z xPack
      `arm-none-eabi-gcc-xpack` gdy brak w PATH/cache) w
      `src/main/kotlin/com/anjo/kopico/provisioning/ArmToolchainProvisioner.kt`
      (FR-012, FR-013)
- [ ] T020 [P] [US2] Zaimplementuj `PicotoolProvisioner` (pobranie + cache z
      release'ów `raspberrypi/pico-sdk-tools`) w
      `src/main/kotlin/com/anjo/kopico/provisioning/PicotoolProvisioner.kt`
      (FR-013)
- [ ] T021 [P] [US2] Zaimplementuj `OpenOcdProvisioner` (pobranie + cache z
      release'ów `raspberrypi/pico-sdk-tools`) w
      `src/main/kotlin/com/anjo/kopico/provisioning/OpenOcdProvisioner.kt`
      (FR-013)
- [ ] T022 [US2] Testy Kotest dla provisionerów w
      `src/test/kotlin/com/anjo/kopico/provisioning/ProvisionersTest.kt`,
      obejmujące explicite: trafienie/brak trafienia w cache, błąd sumy
      kontrolnej → czytelny błąd konfiguracji, **odrzucenie SDK w wersji
      `< 2.2.0`** (FR-011), oraz **użycie toolchaina już dostępnego w
      PATH bez pobierania** (FR-012) (zależy od T018-T021)
- [ ] T023 [US2] Zaimplementuj `CinteropTask` wywołujący CLI `cinterop` dla
      `pico_stdlib`/`hardware_gpio`/`hardware_pwm` (+ `pico_cyw43_arch` dla
      wariantów `_w`) w
      `src/main/kotlin/com/anjo/kopico/tasks/CinteropTask.kt` (FR-005,
      FR-006; zależy od T016, T018)
- [ ] T024 [US2] Zaimplementuj `CompileNativeTask` wywołujący `konanc` z
      custom targetem opartym o `BoardVariant` (wykorzystując ustalenia z
      T009 PoC) w
      `src/main/kotlin/com/anjo/kopico/tasks/CompileNativeTask.kt` (FR-001,
      FR-004; zależy od T023)
- [ ] T025 [P] [US2] Zaimplementuj `Uf2Writer` (ELF → bloki UF2) w
      `src/main/kotlin/com/anjo/kopico/uf2/Uf2Writer.kt` per `research.md`
      § 4 (FR-002)
- [ ] T026 [P] [US2] Testy Kotest dla `Uf2Writer` w
      `src/test/kotlin/com/anjo/kopico/uf2/Uf2WriterTest.kt`
- [ ] T027 [US2] Zaimplementuj `GenerateUf2Task` łączący wyjście
      `CompileNativeTask` z `Uf2Writer` w
      `src/main/kotlin/com/anjo/kopico/tasks/GenerateUf2Task.kt` (FR-007;
      zależy od T024, T025)
- [ ] T028 [US2] Podłącz w `KopicoPlugin` pełny pipeline zadań (Cinterop →
      CompileNative → GenerateUf2) oraz walidację/czytelne błędy
      konfiguracji w fazie configuration w
      `src/main/kotlin/com/anjo/kopico/KopicoPlugin.kt` (FR-008; zależy od
      T016, T023, T024, T027)
- [ ] T029 [US2] Uzupełnij `examples/blink` (`build.gradle.kts` stosujący
      plugin z `board = "pico"`, kod blink w
      `examples/blink/src/nativeMain/kotlin/Main.kt`) per `plan.md` i
      `quickstart.md` Scenariusz B (zależy od T028)
- [ ] T030 [US2] Test funkcjonalny Gradle TestKit uruchamiający pełny build
      `examples/blink` (pierwsze uruchomienie z siecią — z pomiarem czasu i
      asercją `< 15 minut` per SC-002, potem `--offline` rerun per SC-006)
      w
      `src/test/kotlin/com/anjo/kopico/KopicoPluginFunctionalTest.kt` per
      `quickstart.md` Scenariusz B (SC-002, SC-003, SC-006; zależy od T029)
- [ ] T031 [P] [US2] Testy Kotest dla `CinteropTask` asercjące zachowanie
      cinterop CYW43 dla wszystkich 4 wariantów płytek (`pico`/`pico_w`
      obecność/brak `pico_cyw43_arch`, analogicznie `pico2`/`pico2_w`) w
      `src/test/kotlin/com/anjo/kopico/tasks/CinteropTaskTest.kt` (FR-006,
      SC-004; zależy od T023)
- [ ] T032 [US2] Opublikuj plugin lokalnie
      (`./gradlew publishToMavenLocal`) i zweryfikuj, że `examples/blink`
      rozwiązuje go jako zewnętrzny plugin (FR-009; zależy od T028)

**Checkpoint**: User Story 2 w pełni funkcjonalna i testowalna niezależnie
od US1 (poza wykorzystaniem jej ustaleń technicznych z T009/T024).

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Dokumentacja, CI i finalna weryfikacja jakości

- [ ] T033 [P] Napisz `README.md` opisujący użycie pluginu (extension DSL,
      auto-provisioning) z odwołaniem do `contracts/extension-dsl.md` i
      `quickstart.md` (FR-010)
- [ ] T034 [P] Udokumentuj ręczny proces konfiguracji (na bazie
      `poc/SETUP.md` i `poc/konan-target-spike.md`) jako sekcję "jak to
      działa pod maską" w `README.md` (FR-010)
- [ ] T035 [P] Utwórz minimalny, jednojobowy, zautomatyzowany workflow CI
      (np. `.github/workflows/build.yml`) budujący `examples/blink` na
      czystym środowisku Linux przy każdym pushu (SC-005) — jeden job, bez
      publikacji artefaktów/release automation (to pozostaje Fazą 5 z
      `ROADMAP.md`)
- [ ] T036 Uruchom pełną weryfikację przez `ponytail` (ktlint, detekt, cały
      pakiet testów Kotest) dla całego modułu przed uznaniem Fazy 0/1 za
      zakończoną (Zasada V konstytucji)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Brak zależności — start natychmiastowy
- **Foundational (Phase 2)**: Zależy od Setup — BLOKUJE obie user stories
- **User Story 1 (Phase 3)**: Zależy od Foundational; niezależna od US2
  (poza tym, że jej wyniki — T009, T011 — informują implementację T024 w
  US2)
- **User Story 2 (Phase 4)**: Zależy od Foundational; T024 (custom target w
  pluginie) korzysta z ustaleń T009/T011 z US1 — US1 powinna zostać
  ukończona (lub przynajmniej T009 potwierdzone) przed T024
- **Polish (Phase 5)**: Zależy od ukończenia US1 i US2 (T035 dodatkowo
  zależy od T029, bo CI buduje `examples/blink`)

### Parallel Opportunities

- Setup: T002, T003, T004 równolegle po T001
- Foundational: T005, T006 równolegle
- US1: sekwencyjne (T007→T008→T009→T010→T011→T012→T013) — każdy krok
  zależy od poprzedniego stanu środowiska
- US2: T014/T015 równolegle; T017-T021 równolegle po T016; T025/T026
  równolegle niezależnie od reszty US2; T031 równolegle z T024+ po T023
- Polish: T033, T034, T035 równolegle

---

## Parallel Example: Foundational + US2 provisioning

```bash
# Po T016 (KopicoPlugin gotowy), provisionery równolegle:
Task: "Implement PicoSdkProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/PicoSdkProvisioner.kt"
Task: "Implement ArmToolchainProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/ArmToolchainProvisioner.kt"
Task: "Implement PicotoolProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/PicotoolProvisioner.kt"
Task: "Implement OpenOcdProvisioner in src/main/kotlin/com/anjo/kopico/provisioning/OpenOcdProvisioner.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Ukończ Phase 1: Setup
2. Ukończ Phase 2: Foundational
3. Ukończ Phase 3: User Story 1 (PoC) — **STOP i zweryfikuj `poc/RESULTS.md`**
4. Jeśli PoC potwierdza wykonalność → kontynuuj do Phase 4 (US2). Jeśli nie
   → eskaluj do użytkownika przed dalszą pracą (patrz `plan.md` → Summary).

### Incremental Delivery

1. Setup + Foundational → fundament gotowy
2. US1 (PoC) → potwierdzenie wykonalności (bramka go/no-go)
3. US2 (plugin) → pierwszy publikowalny artefakt projektu
4. Polish → dokumentacja, CI i finalna weryfikacja jakości

---

## Notes

- [P] = różne pliki, brak zależności
- [Story] mapuje zadanie do konkretnej user story
- Testy Kotest są obowiązkowe (Zasada III) — pisane przed lub równolegle z
  implementacją danego komponentu
- Każde zadanie implementacyjne (T005+) wykonywane zgodnie z zasadami
  `ponytail` (YAGNI, reużycie, najkrótsza działająca implementacja) — nie
  tylko finalna weryfikacja w T036 (Zasada V konstytucji, v1.1.0)
- Zero kodu w językach innych niż Kotlin (Zasada II) — dotyczy również
  skryptów pomocniczych w `poc/` (patrz T012)
- Commituj po każdym zadaniu lub logicznej grupie zadań, delegując commit
  do agenta `git-committer` (Zasada V)
