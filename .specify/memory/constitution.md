<!--
Sync Impact Report
Version change: 1.2.1 → 1.2.2
Modified principles: none
Added sections: none
Removed sections: none
Modified sections:
  - I. Ścisła kontrola wersji toolchainu — adnotacja "Znany bloker" (v1.2.1)
    zastąpiona "Notą techniczną": runda 3 spike'u potwierdziła działające
    obejście (retargeting linux_arm32_hfp przez -Xoverride-konan-properties
    + patch .bc + stuby C + lld); build Kotlin → UF2 działa end-to-end,
    czeka na walidację sprzętową (T013). Czysto informacyjna aktualizacja
    stanu faktycznego, nie zmiana zasady — stąd PATCH.
Follow-up TODOs:
  - Po pozytywnym T013 (test na fizycznym Pico): usunąć zastrzeżenie
    "walidacja w toku" z noty.
-->

<!--
LEGACY Sync Impact Report (v1.1.0 → v1.2.0, zachowane dla historii)
Version change: 1.1.0 → 1.2.0
Modified principles: none renamed
Added sections: none (existing section expanded)
Removed sections: none
Modified sections:
  - Zakres domenowy: Raspberry Pi Pico Toolchain — dodano konkretne wymagania
    domenowe wyekstrahowane z ROADMAP.md (triple targetów, minimalny kształt
    extension DSL, cinterop CYW43 dla wariantów _w, zadania Gradle dla pełnego
    cyklu build/UF2/flash/debug, zakres przyszłych faz PIO/multicore/power
    management jako niewymagany wcześnie). Odnotowano jawny konflikt: plan
    źródłowy sugerował Kotlin Multiplatform — odrzucony, Zasada I (zakaz KMP)
    pozostaje w mocy bez zmian.
Added artifacts (outside constitution):
  - ROADMAP.md — pełny plan fazowy (Faza 0-5) implementacji pluginu,
    zaadaptowany do custom Kotlin/Native target zamiast KMP.
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no changes needed (Constitution Check gate is generic, resolved at plan time)
  - .specify/templates/spec-template.md ✅ no changes needed (technology-agnostic by design)
  - .specify/templates/tasks-template.md ✅ no changes needed (generic task structure, adjusted per plan)
  - .specify/templates/checklist-template.md ✅ no changes needed
Follow-up TODOs: none
-->

# Kopico Constitution

## Core Principles

### I. Ścisła kontrola wersji toolchainu

Projekt MUSI używać dokładnie Gradle 9.5.1 i Kotlin 2.4.0 — żadnych zakresów
wersji (`+`, `latest.release`) ani wersji zbliżonych. Plugin MUSI działać w
trybie czystego Kotlin/Native (custom native target lub
`org.jetbrains.kotlin.platform.native`) — użycie Kotlin Multiplatform (`kotlin("multiplatform")`)
jest zabronione. Każda zmiana tych wersji wymaga jawnej decyzji i aktualizacji
tej konstytucji.

**Rationale**: Pico SDK, cinterop i generowanie UF2 są wrażliwe na zmiany w
Kotlin/Native ABI i Gradle toolchain resolution; sztywne wersje eliminują całą
klasę błędów "działa u mnie" oraz niekompatybilności między środowiskami CI a
lokalnymi.

**ℹ️ Nota techniczna (2026-07-03, zaktualizowana po rundzie 3 spike'u)**:
Kotlin/Native 2.4.0 nie pozwala zarejestrować *nowej nazwy* targetu, ale
spike (`specs/001-poc-minimal-plugin/poc/`) potwierdził działające
obejście: retargeting istniejącego targetu `linux_arm32_hfp` przez
`-Xoverride-konan-properties` (cortex-m0plus/thumbv6m-none-eabi, static
reloc) + jednorazowy patch atrybutów per-funkcja w runtime `.bc` + warstwa
stubów C (pthread/mmap/TLS) + link przez `ld.lld`. Build Kotlin → UF2
działa end-to-end; walidacja na fizycznym sprzęcie (T013) w toku. "Custom
native target" w tej zasadzie realizowany jest właśnie przez ten mechanizm
retargetingu — pełny przepis: `poc/konan-target-spike.md` § Runda 3.

### II. 100% Kotlin, zero Javy

Cały kod źródłowy, buildscripty i logika pluginu MUSZĄ być napisane w Kotlinie
(Kotlin DSL dla Gradle, brak plików `.java`). Całe logowanie w pluginie MUSI
przechodzić przez `io.github.oshai:kotlin-logging` — zabronione jest bezpośrednie
użycie SLF4J, `println` do logowania stanu, czy innych bibliotek logujących.

**Rationale**: Jednolity język i jedna biblioteka logująca upraszczają
utrzymanie, eliminują konieczność mostkowania Java/Kotlin interop i zapewniają
spójny format logów w całym pluginie.

### III. Test-First w Kotest (NON-NEGOTIABLE)

Wszystkie testy (jednostkowe, integracyjne, testy pluginu z `TestKit`) MUSZĄ
być napisane w Kotest — użycie JUnit bezpośrednio (adnotacje `@Test` z
`org.junit`) jest zabronione, nawet jeśli Kotest uruchamia się przez silnik
JUnit Platform pod spodem. Nowa funkcjonalność publiczna (zadania Gradle,
rozszerzenia DSL, cinterop wrappery) MUSI mieć pokrycie testowe przed
scaleniem.

**Rationale**: Kotest daje czytelne DSL (`FunSpec`, `BehaviorSpec`),
property-based testing i lepsze asercje niż JUnit; jeden framework testowy
zapobiega fragmentacji stylu testów w małym projekcie.

### IV. Best Practices Gradle Plugin

Plugin MUSI być zbudowany zgodnie z uznanymi wzorcami pisania Gradle Pluginów
w Kotlinie: `java-gradle-plugin` do rejestracji i publikacji, convention
plugins do dzielenia wspólnej konfiguracji, jasno odseparowane `extension`
(publiczne DSL) od wewnętrznej logiki `tasks`/`providers`. Publiczny DSL MUSI
być minimalny i deklaratywny — konfiguracja przez `Provider`/`Property`, nie
przez mutowalne pola.

**Rationale**: Zgodność z konwencjami ekosystemu Gradle ułatwia
konfigurowalność (lazy configuration, configuration cache) i obniża koszt
wejścia dla przyszłych kontrybutorów znających standardowe pluginy Gradle.

### V. Jakość i statyczna analiza (NON-NEGOTIABLE)

Każda zmiana kodu MUSI przechodzić `ktlint` (formatowanie) i `detekt`
(statyczna analiza) bez naruszeń przed commitem. Weryfikacja (build, testy
Kotest, ktlint, detekt) MUSI być uruchamiana przez narzędzie `ponytail` przed
każdym commitem. Commity i operacje git (branch, commit, push) MUSZĄ być
wykonywane wyłącznie przez agenta `git-committer`, z wiadomościami w
konwencji Conventional Commits.

**Rationale**: Twarde bramki jakości (formatowanie + statyczna analiza +
testy) uruchamiane spójnym mechanizmem przed każdym commitem zapobiegają
degradacji jakości w małym zespole/solo-projekcie, gdzie nie ma
wielostopniowego code review.

## Zakres domenowy: Raspberry Pi Pico Toolchain

Plugin `com.anjo.kopico` (group `com.anjo`, artifact `kopico`) MUSI
umożliwiać pisanie kodu Kotlin/Native dla Raspberry Pi Pico, Pico W, Pico 2 i
Pico 2 W (mikrokontrolery RP2040 i RP2350). Zakres funkcjonalny obejmuje:
konfigurację toolchaina Kotlin/Native pod docelową architekturę, cinterop z
Pico SDK, generowanie plików UF2 oraz flashowanie urządzenia. Każda nowa
funkcjonalność domenowa MUSI jawnie deklarować, dla których wariantów płytki
(RP2040/RP2350) jest wspierana — brak wsparcia dla wariantu MUSI być
udokumentowany, nie domyślnie zakładany. Szczegółowy harmonogram wdrożenia
(fazy, deliverables, ryzyka) znajduje się w `ROADMAP.md`; ta sekcja definiuje
trwałe wymagania wynikające z tego planu, obowiązujące niezależnie od fazy.

Konkretne wymagania domenowe:

- Plugin MUSI wspierać docelowe triple: `thumbv6m-none-eabi` dla RP2040 oraz
  `thumbv8m.main-none-eabi` / `thumbv8m.main-none-eabihf` dla RP2350,
  konfigurowane przez custom target Kotlin/Native (zgodnie z Zasadą I — bez
  KMP).
- Publiczny extension DSL MUSI udostępniać co najmniej wybór płytki
  (`board = "pico" | "pico_w" | "pico2" | "pico2_w"`) i ścieżkę do Pico SDK
  (`sdkPath`).
- Warianty `_w` (Pico W, Pico 2 W) MUSZĄ automatycznie konfigurować cinterop
  dla `pico_cyw43_arch` (CYW43/WiFi) — warianty bez `_w` MUSZĄ pomijać ten
  cinterop domyślnie.
- Plugin MUSI dostarczać zadania Gradle pokrywające pełny cykl: budowanie
  binarki, generowanie UF2, flashowanie (przez `picotool` lub OpenOCD) i
  debugowanie (GDB + OpenOCD) — nazwy zadań i dokładny zakres ustala plan
  fazy zgodnie z `ROADMAP.md`.
- Docelowo (Faza 4+ w `ROADMAP.md`) zakres obejmuje PIO, `pico_multicore`
  i tryby oszczędzania energii — te funkcje NIE są wymagane w pierwszych
  fazach i nie blokują wcześniejszych wydań.

## Workflow deweloperski i bramki jakości

Narzędzie `ponytail` MUSI być używane zarówno do **implementacji**, jak i do
egzekucji/weryfikacji poleceń. Oznacza to, że sam kod (nowe klasy, zadania
Gradle, rozszerzenia DSL, cinterop wrappery) MUSI być pisany zgodnie z
zasadami ponytail (YAGNI, reużycie istniejących mechanizmów przed napisaniem
nowych, stdlib/API platformy przed zależnością, najkrótsza działająca
implementacja) — nie tylko uruchamiane przez nie na etapie weryfikacji.
Build, testy, lint i statyczna analiza MUSZĄ być egzekwowane i weryfikowane
przez `ponytail`. Do tworzenia i zarządzania commitami (commit, branch, push)
MUSI być używany agent `git-committer` — bezpośrednie wywołania `git
commit`/`git push` przez innego wykonawcę są zabronione. Commit messages
MUSZĄ być zgodne z Conventional Commits (`feat:`, `fix:`, `refactor:`,
`test:`, `docs:`, `chore:` itd.). Przed każdym istotnym commitem MUSI zostać
uruchomiona pełna weryfikacja (kompilacja, testy Kotest, ktlint, detekt)
przez `ponytail`.

## Governance

Ta konstytucja nadrzędna jest wobec wszelkich innych praktyk, szablonów i
dokumentacji w repozytorium — w razie konfliktu wygrywa konstytucja.
Poprawki wymagają: (1) jawnego opisu zmiany i uzasadnienia, (2) aktualizacji
numeru wersji zgodnie z semantycznym wersjonowaniem poniżej, (3) propagacji
zmian do zależnych szablonów (`plan-template.md`, `spec-template.md`,
`tasks-template.md`) jeśli zasady tego wymagają.

Wersjonowanie konstytucji stosuje semver:
- **MAJOR** — usunięcie lub redefinicja zasady w sposób niekompatybilny
  wstecz (np. zmiana wymaganej wersji Kotlin/Gradle, dopuszczenie Javy).
- **MINOR** — dodanie nowej zasady lub istotne rozszerzenie wytycznych.
- **PATCH** — doprecyzowania, poprawki redakcyjne, zmiany niesemantyczne.

Każdy plan (`plan.md`) i przegląd kodu MUSI zawierać sekcję Constitution
Check weryfikującą zgodność z zasadami I–V powyżej. Złamanie zasady bez
udokumentowanego uzasadnienia w `Complexity Tracking` blokuje scalenie.

**Version**: 1.2.2 | **Ratified**: 2026-07-02 | **Last Amended**: 2026-07-03
