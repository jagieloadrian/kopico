# Research: PoC & Minimalny Plugin Kotlin/Native dla Pico

## 1. Wykonalność bare-metal ARM jako target Kotlin/Native (RYZYKO KRYTYCZNE)

**Decision**: Faza 0 (spike, timebox 1-2 tygodnie zgodnie z `ROADMAP.md`)
jest jawnie traktowana jako **bramka go/no-go** dla całego projektu, nie
formalność. Podejście: rozszerzenie definicji targetu Kotlin/Native (mechanizm
`konan.properties`/toolchain config) o custom target `armv6-m`
(`thumbv6m-none-eabi`) z użyciem `picolibc` jako libc/runtime dla środowiska
bez OS, zamiast czekać na oficjalne wsparcie.

**Rationale**: Zweryfikowano źródłowo:
- Oficjalna lista wspieranych targetów Kotlin/Native
  (kotlinlang.org/docs/native-target-support.html) obejmuje wyłącznie
  hostowane systemy (macOS/iOS/Linux/Android/Windows/watchOS/tvOS) — **brak
  jakiegokolwiek targetu bare-metal/freestanding**.
- Ticket JetBrains **KT-44498** ("Add RP2040 As A Kotlin Native Target")
  jest **otwarty, nieprzypisany, bez zaplanowanej wersji** — czyste zgłoszenie
  backlogowe, nie feature w toku.
- Istnieje aktywna, ale **niedokończona** próba społeczności (Raspberry Pi
  Forums, Kotlin Slack #compiler) użycia custom targetu `armv6-m` przez
  `konan.properties` + `picolibc`. Runtime Kotlin/Native (GC, refleksja,
  model pamięci) zakłada hostowany libc/OS — kontrybutorzy raportują
  nierozwiązane blokery przy budowaniu samego runtime'u K/N pod RP2040.
  **Brak dowodu działającego end-to-end builda produkującego UF2** ze
  standardowym kompilatorem, ale też brak dowodu, że jest to definitywnie
  niemożliwe.

**Alternatives considered**:
- Czekać na oficjalne wsparcie KT-44498 — odrzucone: brak harmonogramu,
  blokowałoby cały projekt na czas nieokreślony.
- Zrezygnować z Kotlin/Native na rzecz C z cienkim wrapperem — odrzucone:
  łamie fundamentalne założenie projektu (Zasada I/II konstytucji).
- Kotlin/Wasm lub inny backend — odrzucone: nie generuje natywnego kodu ARM
  wykonywalnego na mikrokontrolerze bez OS.

**Implikacja dla planu**: Jeśli Faza 0 nie potwierdzi wykonalności w
zadeklarowanym czasie (2 tygodnie, SC-001), wymagana jest natychmiastowa
eskalacja do użytkownika i rewizja `ROADMAP.md`/konstytucji — plan zakłada
sukces, ale explicit tego nie gwarantuje (stąd status PoC, nie
"implementacja").

## 2. Mechanizm cinterop bez KMP

**Decision**: Narzędzie `cinterop` jest dystrybuowane jako samodzielny plik
wykonywalny w dystrybucji kompilatora Kotlin/Native (`<konan-dist>/bin/cinterop`),
niezależny od pluginu `kotlin("multiplatform")`. Plugin wywołuje go
bezpośrednio przez Gradle `Exec`/`ExecOperations`, z plikiem `.def`
wskazującym nagłówki Pico SDK (`pico_stdlib`, `hardware_gpio`,
`hardware_pwm`, opcjonalnie `pico_cyw43_arch`).

**Rationale**: Niskie ryzyko — to udokumentowany, stabilny interfejs CLI
kompilatora, używany też wewnętrznie przez plugin KMP.

**Alternatives considered**: Reimplementacja cinterop — odrzucone,
niepotrzebne (ladder ponytail: użyj istniejącego narzędzia).

## 3. Dystrybucja kompilatora Kotlin/Native

**Decision**: Plugin pobiera oficjalną dystrybucję Kotlin/Native 2.4.0 dla
Linux x86_64 i cache'uje ją lokalnie (FR-013/FR-014), następnie wywołuje
`bin/konanc` z opcjami custom targetu (patrz punkt 1) przez Gradle `Exec`.
**Zweryfikowane w Fazie 0 (T008)**: `download.jetbrains.com` zwraca 404 dla
tej dystrybucji — realny, działający URL to GitHub Releases:
`https://github.com/JetBrains/kotlin/releases/download/v2.4.0/kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz`
(plus `.sha256` w tym samym release, do weryfikacji sumy kontrolnej).

**Rationale**: Spójne z FR-013 (auto-provisioning "toolchaina") — sam
kompilator K/N jest częścią toolchaina niezbędnego do budowy, mimo że spec
explicite wymienia "Pico SDK, toolchain ARM, picotool, OpenOCD"; kompilator
K/N jest domyślnym, oczywistym elementem bez którego FR-001 nie da się
zrealizować, więc traktowany jest jako podzbiór "toolchain ARM" w duchu
FR-013, a nie nowy zakres.

**Alternatives considered**: Wymagać ręcznej instalacji Kotlin/Native przez
dewelopera — odrzucone, niespójne z decyzją auto-provisioningu (Clarifications
w spec.md).

## 4. Generowanie UF2

**Decision**: Format UF2 implementowany natywnie w Kotlinie
(`Uf2Writer.kt`) — czyta sekcje z pliku ELF i zapisuje bloki UF2 (256-bajtowe
bloki z nagłówkiem `UF2\n`, zgodnie z publiczną specyfikacją formatu Microsoft
UF2).

**Rationale**: Format UF2 jest prosty i w pełni udokumentowany publicznie;
implementacja natywna eliminuje zależność od zewnętrznego binarnego narzędzia
`elf2uf2` (które i tak wymagałoby budowania z źródeł — nie ma prebuilt
release'ów), spójne z Zasadą II (100% Kotlin) i ladder ponytail (mniej
zależności zewnętrznych, gdy format jest trywialny do zaimplementowania).

**Alternatives considered**: Shell-out do `elf2uf2` z `pico-sdk-tools` —
odrzucone jako pierwszy wybór (dodatkowa zależność binarna bez prebuilt
release), zachowane jako potencjalny fallback, jeśli natywna implementacja
napotka nieoczekiwane problemy w Fazie 0.

## 5. Źródła auto-provisioningu narzędzi (FR-013/FR-014)

Zweryfikowane źródłowo, konkretne URL-e i format:

| Narzędzie | Źródło | Format | Uwagi |
|---|---|---|---|
| Pico SDK | `github.com/raspberrypi/pico-sdk`, pinned tag `2.2.0` | shallow git clone `--recurse-submodules` | tarball źródłowy nie zawiera submodułów (np. `tinyusb`) — wymagany git clone |
| Toolchain ARM (`arm-none-eabi-gcc`) | `github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases` | prebuilt tarball `xpack-arm-none-eabi-gcc-{VERSION}-linux-x64.tar.gz` + `.sha` checksum | idealne pod automatyzację — wersjonowane, z sumami kontrolnymi |
| `picotool` | `github.com/raspberrypi/pico-sdk-tools/releases` (NIE `raspberrypi/picotool` — tam tylko źródła) | prebuilt `picotool-{VERSION}-*-x86_64-lin.tar.gz` | |
| OpenOCD (fork RP2040/RP2350) | `github.com/raspberrypi/pico-sdk-tools/releases` (NIE `raspberrypi/openocd` — brak Releases, tylko tagi git) | prebuilt `openocd-{VERSION}-x86_64-lin.tar.gz` | |

**Decision**: Wszystkie cztery narzędzia pobierane z powyższych,
zweryfikowanych źródeł; `picotool` i OpenOCD z jednego wspólnego repo
(`pico-sdk-tools`), co upraszcza logikę provisioningu (jeden dostawca
release'ów dla obu).

**Rationale**: Unika najczęstszego błędu w tym obszarze — repo
`raspberrypi/picotool` i `raspberrypi/openocd` NIE publikują prebuilt
binarek (tylko źródła/tagi); realny adres prebuilt artefaktów to
`raspberrypi/pico-sdk-tools`.

**Alternatives considered**: Budowanie `picotool`/OpenOCD ze źródeł przez
CMake w ramach auto-provisioningu — odrzucone dla tej fazy: znacząco
zwiększa czas pierwszego builda i złożoność (wymaga CMake + kompilatora hosta
jako dodatkowej zależności), niespójne z SC-002 (< 15 min pierwszego builda).
Może wrócić jako fallback, jeśli `pico-sdk-tools` nie publikuje binarki dla
danej wersji/platformy.

## Cache lokalny (wspólne dla wszystkich czterech narzędzi)

**Decision**: `<gradleUserHome>/caches/kopico/<narzędzie>/<wersja>/` —
wykorzystuje istniejącą konwencję Gradle User Home zamiast wynajdywać własną
lokalizację cache.

**Rationale**: Zgodne z Zasadą IV (konwencje ekosystemu Gradle) i ladder
ponytail (reuse istniejącego mechanizmu zamiast pisania nowego). Gradle User
Home jest już czyszczony/zarządzany przez istniejące narzędzia (`gradle
--stop`, cache cleanup), więc plugin nie musi implementować własnej logiki
czyszczenia w tej fazie.
