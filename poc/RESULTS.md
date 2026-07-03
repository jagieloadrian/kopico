# PoC Results — Bramka go/no-go (T013)

**Data**: 2026-07-03

## Werdykt: **PORAŻKA** (w obecnym zakresie i podejściu)

| Krok | Status | Dowód |
|---|---|---|
| T007 — toolchain ARM + Pico SDK 2.2.0 | ✅ Sukces | `poc/SETUP.md` |
| T008 — kompilator Kotlin/Native 2.4.0 | ✅ Sukces (po korekcie URL) | `poc/SETUP.md` |
| T009 — custom target `thumbv6m-none-eabi` | ❌ **Porażka empiryczna** | `poc/konan-target-spike.md` |
| T010 — cinterop dla `pico_stdlib`/`hardware_gpio` | ⏸️ Zablokowane przez T009 | — |
| T011 — kompilacja blink do ELF | ⏸️ Zablokowane przez T009 | — |
| T012 — konwersja ELF → UF2 | ⏸️ Zablokowane przez T011 | — |
| T013 — flash na fizyczne urządzenie | ⏸️ Zablokowane przez T012 + brak dostępu do sprzętu w tym środowisku | — |

## Przyczyna źródłowa

Kotlin/Native 2.4.0 waliduje flagę `-target` względem zamkniętego,
wkompilowanego w binarkę enuma `KonanTarget` — **przed** jakimkolwiek
odczytem `konan.properties`. Nie istnieje mechanizm rozszerzenia listy
targetów przez konfigurację; wymagałoby to forka/patcha i rekompilacji
samego kompilatora (`JetBrains/kotlin`), a dodatkowo dostosowania runtime'u
K/N (zakłada hostowany OS: `mmap`, wątki, glibc) do środowiska bare-metal
bez OS. To wielokrotnie większe przedsięwzięcie niż "spike PoC" — realnie
osobny, wieloletni projekt na poziomie współpracy z JetBrains/community
(patrz stan ticketu KT-44498 w `research.md` § 1: otwarty, bez przypisania,
bez planowanej wersji).

## Rekomendacja

**Nie kontynuować Fazy 4 (US2 — plugin) w obecnym kształcie**, dopóki
fundamentalne założenie architektoniczne (Kotlin/Native na bare-metal
Cortex-M przez custom target) nie zostanie zweryfikowane jako wykonalne
innym sposobem lub świadomie zaakceptowane jako projekt dużo większy niż
"Faza 0/1: PoC + minimalny plugin". To wymaga decyzji użytkownika — nie
techniczna kontynuacja implementacji.

Możliwe kierunki (do decyzji, nie zrealizowane w ramach tego spike'u):
1. **Fork kompilatora Kotlin/Native** — dodać `KonanTarget` dla Cortex-M +
   dostosować runtime pod `picolibc`/freestanding. Wielomiesięczny,
   wysokim ryzyku projekt badawczy, nie "plugin Gradle".
2. ~~**Zmiana architektury**: pośredni artefakt LLVM/C~~ — **ZBADANE i
   ODRZUCONE (2026-07-03)**. `-produce bitcode` jest jawnie wyłączone w
   2.4.0 (`error: Bitcode output kind is obsolete`). `-Xsave-llvm-ir-after`
   działa tylko w ramach kompilacji dla już wybranego, wspieranego targetu
   — nie omija wymogu `-target`. Decydujący dowód: `runtime.bc` (GC,
   alokator, obsługa wyjątków K/N) jest prekompilowany **per-target** i
   istnieje wyłącznie dla wspieranych targetów — nie ma generycznej wersji.
   Nawet wydobyty LLVM IR byłby zlinkowany z runtime'em dla hostowanego OS
   i nie dałby się dalej skompilować pod bare-metal — ta sama przyczyna
   źródłowa co custom target (opcja 1).
3. **Zawężenie zakresu projektu**: Kotlin/Native tylko na hostowany Linux
   ARM (np. Raspberry Pi pełnoprawny SBC, nie mikrokontroler Pico) —
   zmienia fundamentalnie cel projektu z `ROADMAP.md`.
4. **Zaakceptować ryzyko i przeznaczyć realny budżet** (miesiące, nie
   tygodnie) na fork kompilatora jako osobny, jawnie nazwany
   sub-projekt/milestone.

**To nie jest decyzja, którą mogę podjąć autonomicznie** — zmienia
fundamentalne założenia `ROADMAP.md` i konstytucji projektu.
