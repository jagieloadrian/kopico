# Kopico Roadmap

Plan implementacji Gradle Pluginu `com.anjo.kopico` dla Kotlin/Native na
Raspberry Pi Pico / Pico 2 (RP2040 / RP2350).

> **🛑 STATUS: Faza 0 zablokowana (2026-07-03).** Spike techniczny
> (`specs/001-poc-minimal-plugin/poc/`) empirycznie obalił kluczowe
> założenie architektoniczne: Kotlin/Native 2.4.0 **nie pozwala**
> zarejestrować custom targetu (np. `thumbv6m-none-eabi`) przez
> `konan.properties` — kompilator waliduje `-target` względem zamkniętej
> listy wkompilowanej w binarkę, zanim w ogóle odczyta plik properties.
> Wymagałoby to forka i rekompilacji samego kompilatora Kotlin/Native oraz
> dostosowania jego runtime'u do środowiska bez OS — przedsięwzięcie
> wielokrotnie większe niż "Faza 0/1: PoC + minimalny plugin". Pełny dowód
> i analiza opcji dalszego kierunku:
> `specs/001-poc-minimal-plugin/poc/RESULTS.md` i
> `specs/001-poc-minimal-plugin/poc/konan-target-spike.md`. **Fazy 1-5
> poniżej pozostają jako zapis pierwotnego planu, ale ich realizacja w
> obecnym kształcie jest zawieszona do czasu podjęcia decyzji o dalszym
> kierunku architektonicznym.**

> **Uwaga architektoniczna**: zgodnie z Zasadą I konstytucji projektu
> (`.specify/memory/constitution.md`), plugin działa w trybie **czystego
> Kotlin/Native** (custom native target), **nie** Kotlin Multiplatform.
> Wszystkie poniższe fazy zakładają konfigurację przez dedykowany, custom
> target Kotlin/Native (odpowiednik `KotlinNativeTarget` skonfigurowanego
> ręcznie/przez plugin, bez bloku `kotlin("multiplatform")`), a nie standardowy
> mechanizm KMP presetów.

## Zakres

Plugin ma umożliwić wygodne pisanie kodu w Kotlin/Native (z użyciem Pico SDK) dla:

- **RP2040**: Pico + Pico W
- **RP2350**: Pico 2 + Pico 2 W

Integruje się z toolchainem Pico SDK (CMake + `arm-none-eabi-gcc`/clang +
OpenOCD / picotool).

### Cele pluginu

- Ułatwienie deklaracji targetów `pico`, `picoW`, `pico2`, `pico2W`.
- Automatyczna konfiguracja cinterop z Pico SDK.
- Wsparcie dla bootloadera (UF2), flashowania, debugowania.
- Wsparcie dla wersji z WiFi (CYW43).
- Łatwe budowanie i deploy (Makefile-like experience w Gradle).
- Kompatybilność z istniejącymi projektami Pico SDK (C/C++ interop).

## Faza 0: Research & PoC (1-2 tygodnie)

**Analiza istniejących rozwiązań**
- Sprawdź ticket JetBrains: KT-44498 – dodanie RP2040 jako targetu.
- Przeanalizuj jak działają custom targety w Kotlin/Native (bez KMP presetów).
- Zbadaj istniejące cinterop z Pico SDK (header-only podejście jest możliwe).

**Techniczne PoC**
- Stwórz ręczny projekt Kotlin/Native z custom target skonfigurowanym pod
  triple ARM Pico (bez `kotlin("multiplatform")`).
- Skonfiguruj cinterop dla `pico-sdk` (headers z `pico-sdk/src/rp2_common`).
- Skompiluj prosty blink w Kotlinie (używając `gpio_put` itp.).
- Wygeneruj UF2 i sprawdź na hardware.

**Narzędzia**
- Zainstaluj Pico SDK + `arm-none-eabi-gcc`.
- Zdefiniuj triple: `thumbv6m-none-eabi` (RP2040) i
  `thumbv8m.main-none-eabi` / `thumbv8m.main-none-eabihf` (RP2350).

**Deliverable**: Repozytorium z działającym PoC + dokumentacja "jak to działa manualnie".

## Faza 1: Podstawowy Gradle Plugin (2-3 tygodnie)

**Cel**: Minimalny działający plugin.

**Zadania**:
- Utwórz plugin Gradle `com.anjo.kopico` używając `java-gradle-plugin` +
  Kotlin DSL.
- Dodaj extension:
  ```kotlin
  pico {
      sdkPath = "/path/to/pico-sdk"
      board = "pico" // "pico_w" / "pico2" / "pico2_w"
      // inne opcje: frequency, debug, etc.
  }
  ```
- Zarejestruj custom native target odpowiedni dla wybranej płytki (bez
  bloku KMP `kotlin { }` z presetami — target konfigurowany bezpośrednio
  przez plugin pod właściwy triple).
- Wsparcie dla wariantów W (dodatkowe cinteropy dla CYW43).
- Automatyczna konfiguracja cinterops dla kluczowych bibliotek Pico SDK
  (`pico_stdlib`, `hardware_gpio`, `hardware_pwm` itd.).

**Deliverable**: Plugin publikowalny (Maven Local / GitHub Packages) +
przykładowy projekt blink.

## Faza 2: Integracja z Pico SDK i Build System (3-4 tygodnie)

**Zaawansowane cinterop**
- Automatyczne generowanie plików `.def` dla całego SDK lub wybranych modułów.
- Wsparcie dla `pico-sdk` jako submodułu lub external project.
- Obsługa integracji z CMake (wywołanie CMake z Gradle via `exec` lub
  dedykowany task).

**Binaries & Linking**
- Konfiguracja linker scriptów (`memmap_default.ld` itp.).
- Generowanie UF2 (`elf2uf2` lub port na JVM/Kotlin).
- Wsparcie dla odpowiedników `pico_add_library` / `pico_enable_stdio_usb` itp.

**Wersje W**
- Dedykowane targety `picoW` / `pico2W`.
- Automatyczne dodanie `pico_cyw43_arch` i lwIP.

**Deliverable**: Przykłady GPIO, UART, ADC, PWM, WiFi (na W).

## Faza 3: Developer Experience & Tooling (2-3 tygodnie)

**Taski Gradle**:
- `buildPico` / `buildUf2`
- `flash` (przez `picotool` lub OpenOCD)
- `debug` (GDB + OpenOCD)
- `monitor` (minicom / picotool)

**Konwencje i templates**:
- Convention plugins (np. `pico.kotlin`).
- Gotowe szablony projektów (`gradle init`).

**Testowanie**:
- Testy jednostkowe na hoście (Kotest, zgodnie z Zasadą III) + integration na
  hardware (jeśli możliwe).
- Mocki dla hardware (opcjonalnie).

**Dokumentacja**:
- README z przykładami.
- Przewodnik "Migracja z C SDK do Kotlin".

**Deliverable**: Pełny przykład aplikacji (np. USB CDC + LED + WiFi na Pico W).

## Faza 4: Zaawansowane funkcje & Optymalizacje (2-4 tygodnie)

- Wsparcie dla RP2350 (ARM; rdzeń RISC-V poza pierwszym zakresem — start od ARM).
- Bootloader 2nd stage.
- PIO (Programmable I/O) – bindings.
- Multicore (`pico_multicore`).
- Power management, sleep modes.
- Integracja z `tinyusb`, `lwip`, `freertos` (opcjonalnie).
- Optymalizacje rozmiaru binarek (`-Os`, stripping).
- Publikacja pluginu na Gradle Plugin Portal.

## Faza 5: Testowanie, Dokumentacja, Release

- Testy na rzeczywistym hardware (wszystkie 4 warianty: Pico, Pico W, Pico 2, Pico 2 W).
- Przykłady community (Blink, Hello World, Sensor, WiFi server).
- CI/CD (GitHub Actions – budowanie na Linux).
- Licencja (Apache 2.0 / MIT).
- Publikacja + ogłoszenie (Reddit, Raspberry Pi forums, Kotlin Slack).

## Ryzyka / Wyzwania

- Brak oficjalnego wsparcia targetu w Kotlin/Native → custom target może być niestabilny.
- Zmiany w Pico SDK (szczególnie RP2350).
- Rozmiar i performance (Kotlin/Native ma overhead względem czystego C).
- Debugowanie na bare-metal.
