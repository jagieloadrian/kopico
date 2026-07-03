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
2. **Zmiana architektury**: Kotlin/Native kompiluje do C/LLVM IR
   pośredniego, a finalne linkowanie/dostosowanie pod RP2040 robi
   zewnętrzny toolchain — wymaga zbadania, czy K/N eksportuje taki
   pośredni artefakt w użytecznej formie (nie zbadane w tym spike'u).
3. **Zawężenie zakresu projektu**: Kotlin/Native tylko na hostowany Linux
   ARM (np. Raspberry Pi pełnoprawny SBC, nie mikrokontroler Pico) —
   zmienia fundamentalnie cel projektu z `ROADMAP.md`.
4. **Zaakceptować ryzyko i przeznaczyć realny budżet** (miesiące, nie
   tygodnie) na fork kompilatora jako osobny, jawnie nazwany
   sub-projekt/milestone.

**To nie jest decyzja, którą mogę podjąć autonomicznie** — zmienia
fundamentalne założenia `ROADMAP.md` i konstytucji projektu.
