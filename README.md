# kopico

Plugin Gradle `com.anjo.kopico` — kompilacja Kotlin/Native na Raspberry Pi
Pico (RP2040/RP2350), od kodu Kotlin do gotowego pliku UF2, bez ręcznej
konfiguracji toolchaina.

Wykonalność potwierdzona na fizycznym sprzęcie (Pico W, `poc/RESULTS.md`).

## Użycie

```kotlin
plugins {
    id("com.anjo.kopico") version "0.1.0-SNAPSHOT"
}

pico {
    board = "pico"          // wymagane: "pico" | "pico_w" | "pico2" | "pico2_w"
    // sdkPath = file("...") // opcjonalne — brak = automatyczne pobranie Pico SDK
}
```

```bash
./gradlew build
# wynik: build/kopico/<nazwa-projektu>.uf2
```

Kod aplikacji trafia do `src/nativeMain/kotlin/`. API GPIO/LED jest dostępne
przez pakiet `pico` (cinterop z wrapperem `kopico.h`) — patrz
`examples/blink/`. Pełny kontrakt DSL: `specs/001-poc-minimal-plugin/contracts/extension-dsl.md`,
scenariusze walidacji: `specs/001-poc-minimal-plugin/quickstart.md`.

### Auto-provisioning (FR-013)

Przy pierwszym buildzie (wymagana sieć) plugin pobiera i cache'uje w
`<gradleUserHome>/caches/kopico/<narzędzie>/<wersja>/`:

| Narzędzie | Wersja | Źródło |
|---|---|---|
| Pico SDK | 2.2.0 | shallow git clone `raspberrypi/pico-sdk` |
| ARM GCC | 15.2.1-1.1 | xPack `arm-none-eabi-gcc-xpack` (z weryfikacją SHA-256) |
| Kotlin/Native | 2.4.0 | GitHub Releases `JetBrains/kotlin` (z weryfikacją SHA-256) |
| picotool | 2.2.0-a4 | `raspberrypi/pico-sdk-tools` |
| OpenOCD | 0.12.0+dev | `raspberrypi/pico-sdk-tools` (na potrzeby przyszłych zadań flash/debug) |

Narzędzia obecne w `PATH` (`arm-none-eabi-gcc`, `picotool`) mają
pierwszeństwo przed pobieraniem (FR-012). Jawnie ustawiony `sdkPath` jest
walidowany (wymagane `>= 2.2.0`, FR-011) i nigdy nie jest nadpisywany.
Kolejne buildy działają offline (FR-014).

## Jak to działa pod maską

Kotlin/Native nie wspiera oficjalnie bare-metal ARM Cortex-M. Plugin
automatyzuje przepis wypracowany i zweryfikowany sprzętowo w PoC
(`poc/konan-target-spike.md`, `poc/SETUP.md`):

1. **Retargeting zamiast forka kompilatora.** Rejestracja nowego targetu w
   `konan.properties` nie działa (nazwa targetu jest walidowana względem
   zamkniętego enuma w kompilatorze). Zamiast tego plugin przejmuje
   istniejący target `linux_arm32_hfp` i nadpisuje jego codegen flagą
   `-Xoverride-konan-properties`: `targetCpu=cortex-m0plus`,
   `targetTriple=thumbv6m-none-eabi`, soft-float, Thumb-only, relokacje
   statyczne (`KonanRetargeting.kt`).

2. **Patch atrybutów `.bc` runtime'u.** Pliki `konan/targets/linux_arm32_hfp/native/*.bc`
   mają wkompilowane per-funkcyjne atrybuty LLVM (`target-cpu=arm1176jzf-s`,
   `-thumb-mode`), które wygrywają z triple i generują kod ARM-mode —
   nielegalny na Armv6-M. `KotlinNativeProvisioner` wykonuje jednorazowy
   patch (`clang -x ir → sed → clang -c -emit-llvm`, backup w `native.bak`,
   marker chroni przed powtórką).

3. **Pipeline zadań** (`kopicoCinterop → kopicoCompileNative → kopicoLink →
   kopicoUf2`, podpięty pod `assemble`/`build`):
   - `cinterop` buduje klib z wrappera `kopico.h` (te same override'y co
     konanc — bridge w klibie też niesie atrybuty ARM);
   - `konanc -produce static` z `-Xbinary=gc=noop -Xbinary=gcSchedulerType=manual
     -Xallocator=std` (RP2040 nie ma OS/MMU — bez wątków GC) daje `libkotlinapp.a`;
   - link finalnego ELF robi CMake z Pico SDK (boot2, crt0, clocks) — plugin
     wstrzykuje własne zasoby: `wrapper.c` (most GPIO/CYW43 + wywołanie
     `main` z Kotlina), `kopico_shim.c` (stuby pthread/mmap/TLS —
     środowisko jednowątkowe bez MMU), `kopico_stdio_globals.c`
     (stdout/stderr jako symbole dla newlib) i `memmap_kopico.ld` (`.got`
     we FLASH); linkowanie przez `ld.lld` z zależności K/N, bo `ld.bfd` nie
     trawi relokacji Thumb z obiektów LLVM;
   - `picotool uf2 convert` zamienia ELF na UF2.

4. **Pico W / Pico 2 W:** dioda LED wisi na chipie WiFi CYW43, nie na
   GPIO25. Wrapper kieruje operacje LED przez pin-sentinel na
   `cyw43_arch_gpio_put`, a plugin dolinkowuje `pico_cyw43_arch_none` tylko
   dla wariantów `_w` — kod Kotlin pozostaje wspólny.

## Rozwój

```bash
./gradlew test ktlintCheck detekt   # pełna weryfikacja
./gradlew publishToMavenLocal       # publikacja lokalna dla examples/
KOPICO_E2E=1 ./gradlew test         # pełny test funkcjonalny (sieć, ~kilkanaście minut)
./gradlew -p examples/blink build   # przykład end-to-end
```

Wymagania hosta: Linux x86_64, JDK 21, `git`, `cmake`, `tar`.
