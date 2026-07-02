<!--
Sync Impact Report
Version change: 1.0.0 → 1.1.0
Modified principles: none renamed
Added sections: none (existing section expanded)
Removed sections: none
Modified sections:
  - Workflow deweloperski i bramki jakości: rozszerzono o wymóg, że `ponytail`
    MUSI być używane również do samej implementacji kodu (zasady YAGNI/reużycia/
    minimalnej implementacji), nie tylko do egzekucji poleceń weryfikacyjnych.
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
udokumentowany, nie domyślnie zakładany.

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

**Version**: 1.1.0 | **Ratified**: 2026-07-02 | **Last Amended**: 2026-07-02
