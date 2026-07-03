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

## Znane ograniczenia PoC

- `gc=noop` — bez zwalniania pamięci (dla długożyjących aplikacji do
  zbadania `gc=stms` + shim wątków).
- Target `linux_arm32_hfp` deprecated (wersja K/N przypięta, więc stabilne).
- ~266KB flash / ~72KB RAM na runtime — optymalizacja to Faza 4.

## Następne kroki

1. **Faza 4 tasks.md (US2, T014-T032)**: implementacja właściwego pluginu
   `com.anjo.kopico` — extension DSL, provisionery, `CinteropTask`,
   `CompileNativeTask` (wg przepisu z PoC), `Uf2Writer` w Kotlinie,
   `examples/blink`, testy TestKit.
2. **Polish (T033-T036)**: README, dokumentacja "pod maską", minimalny CI
   (jeden job Linux), finalna weryfikacja ponytail.
3. Rozważyć: automatyzację patcha `.bc` i generacji shim/wrapper jako
   zadań pluginu (kluczowe dla FR-013 provisioning).
