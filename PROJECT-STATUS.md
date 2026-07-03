# PROJECT STATUS — kopico

Ostatnia aktualizacja: 2026-07-03

## Ukończone

- **Konstytucja projektu** (`.specify/memory/constitution.md`, v1.2.3):
  Gradle 9.5.1 / Kotlin 2.4.0 (dokładnie), bez KMP, 100% Kotlin +
  kotlin-logging, testy wyłącznie Kotest, ktlint+detekt, ponytail do
  implementacji i weryfikacji, commity przez git-committer.
- **ROADMAP.md**: plan faz 0-5 (PoC → plugin → SDK integration → DX →
  advanced → release).
- **Spec + plan + tasks** dla Fazy 0/1: `specs/001-poc-minimal-plugin/`
  (spec.md po 3 rundach clarify, plan.md z research.md, tasks.md 36 zadań,
  analiza cross-artifact czysta — 100% pokrycia wymagań).
- **Setup + Foundational (T001-T006)**: scaffold pluginu
  (`java-gradle-plugin`, ktlint, detekt, Kotest, TestKit), `BoardVariant`
  enum + testy — build zielony.
- **✅ FAZA 0 (PoC) ZAKOŃCZONA — T007-T013**: blink napisany w Kotlinie
  działa na fizycznym Raspberry Pi Pico W. Pipeline: `konanc` z
  retargetingiem `linux_arm32_hfp`→cortex-m0plus → `libkotlinapp.a` →
  CMake+pico-sdk+lld → ELF → picotool → UF2. Przepis:
  `poc/konan-target-spike.md`; werdykt: `poc/RESULTS.md`.
- **✅ FAZA 1 (US2 + Polish) ZAKOŃCZONA — T014-T036 (2026-07-03)**: plugin
  `com.anjo.kopico` w pełni funkcjonalny. Extension DSL `pico { board,
  sdkPath }` z walidacją w fazie konfiguracji; provisionery (Pico SDK,
  xPack GCC, picotool, OpenOCD, Kotlin/Native z patchem `.bc` i kompilacją
  rozgrzewkową dla zależności clang); pipeline `kopicoCinterop →
  kopicoCompileNative → kopicoLink → kopicoUf2` podpięty pod `build`.
  Zasoby C z PoC (`wrapper.c`, `kopico_shim.c`, `kopico_stdio_globals.c`,
  `memmap_kopico.ld`, szablon CMake) w `src/main/resources/kopico/`.
  28 testów Kotest + E2E TestKit (`KOPICO_E2E=1`): zero-config build →
  UF2 w 2m16s (SC-002 ✓), `--offline` z cache ✓ (SC-006), `pico_w` bez
  dodatkowej konfiguracji ✓ (SC-003). `examples/blink` buduje się jako
  zewnętrzny konsument z Maven Local (FR-009 ✓). README (użycie + "pod
  maską"), CI: `.github/workflows/build.yml` (SC-005). ktlint/detekt/testy
  zielone.

## Kluczowe decyzje

1. **Bez KMP** — czysty Kotlin/Native (Zasada I konstytucji); plan
   źródłowy sugerował KMP, odrzucone świadomie.
2. **Custom target przez retargeting, nie fork kompilatora**: K/N 2.4.0
   nie rejestruje nowych targetów, ale `-Xoverride-konan-properties`
   pozwala przejąć `linux_arm32_hfp` (cortex-m0plus/thumbv6m, static
   reloc). Wymaga jednorazowego patcha atrybutów w runtime `.bc`
   (clang -x ir → sed → clang) — plugin będzie to robił w swoim cache.
3. **Runtime na bare-metal przez ~150 linii stubów C** (pthread no-op,
   mmap = statyczna arena, `__aeabi_read_tp` naked asm, cond_var stuby,
   stdout/stderr globale) + `-Xbinary=gc=noop -Xallocator=std`.
4. **Link przez `ld.lld`** (bfd nie trawi relokacji Thumb z LLVM) +
   linker script z `.got` we FLASH.
5. **Auto-provisioning narzędzi** (Pico SDK ≥2.2.0, xPack ARM GCC,
   picotool/OpenOCD z `pico-sdk-tools`) z cache w Gradle User Home;
   K/N compiler z GitHub Releases (download.jetbrains.com → 404).
6. **Warianty `_w`**: dioda na CYW43 (nie GPIO25) — build musi być
   per-płytka; `BoardVariant.hasWifi` wpływa też na LED, nie tylko WiFi.
7. **gcc z PATH tylko w pinowanej wersji major (15)**: systemowy
   arm-none-eabi-gcc 13 produkuje ELF z segmentem `.ram_vector_table`
   z zawartością pliku — picotool odrzuca ("memory contents for
   uninitialized memory at 0x20000000"). FR-012 zachowane z bramką wersji.
8. **`Uf2Writer` w Kotlinie porzucony** na rzecz provisionowanego
   `picotool uf2 convert` (reużycie zamiast reimplementacji; picotool
   i tak wymagany przez FR-013).
9. **Jeden klib z wrappera `kopico.h`** zamiast klib-ów per biblioteka
   SDK; biblioteki SDK per wariant dobiera `CinteropTask.sdkLibrariesFor`,
   linkuje CMake. cinterop wymaga ścieżek nagłówków newlib
   (odpytywane z `gcc -E -Wp,-v`) i najnowszego lld z `~/.konan/dependencies`.

## Znane ograniczenia PoC

- `gc=noop` — bez zwalniania pamięci (dla długożyjących aplikacji do
  zbadania `gc=stms` + shim wątków).
- Target `linux_arm32_hfp` deprecated (wersja K/N przypięta, więc stabilne).
- ~266KB flash / ~72KB RAM na runtime — optymalizacja to Faza 4.

## Następne kroki

1. **Merge `feature-1/init-project` → `main`** (feature 001 kompletne:
   36/36 zadań, wszystkie SC spełnione).
2. **ROADMAP Faza 2/3**: głębsza integracja SDK (więcej bibliotek Pico
   SDK w cinterop), zadania `flash`/`debug`/`monitor` (OpenOCD już
   provisionowany), stabilne publiczne nazwy zadań.
3. **Warianty RP2350 (`pico2`/`pico2_w`)**: patch `.bc` i retargeting
   są dziś przypięte do cortex-m0plus/thumbv6m — rozszerzenie o
   cortex-m33 wymaga osobnej kopii dystrybucji K/N per chip (do
   zbadania przy Fazie 2); brak weryfikacji sprzętowej RP2350.
4. Rozważyć weryfikację sprzętową buildu z pluginu (nie tylko PoC) —
   UF2 z `examples/blink` jest bajtowo zbliżony do sprzętowo
   potwierdzonego `kblink.uf2`, ale nie był wgrany na płytkę.
