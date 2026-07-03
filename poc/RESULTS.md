# PoC Results — Bramka go/no-go (T013)

**Data**: 2026-07-03

## Werdykt: **PORAŻKA w pierwotnym podejściu, ale ODKRYTO działającą ścieżkę codegen** (2026-07-03, runda 2)

| Krok | Status | Dowód |
|---|---|---|
| T007 — toolchain ARM + Pico SDK 2.2.0 | ✅ Sukces | `poc/SETUP.md` |
| T008 — kompilator Kotlin/Native 2.4.0 | ✅ Sukces (po korekcie URL) | `poc/SETUP.md` |
| T009 — nowy custom target (nazwa nierozpoznana) | ❌ Porażka empiryczna | `poc/konan-target-spike.md` |
| T009b — **retargeting istniejącego targetu przez `-Xoverride-konan-properties`** | ✅ **Codegen działa** (patrz niżej), ❌ finalny link i runtime nie | `poc/konan-target-spike.md` § "Runda 2" |
| T010-T013 | ⏸️ Nie wykonane — nie ma sensu przed rozstrzygnięciem runtime'u (patrz niżej) | — |

## Runda 2: `-Xoverride-konan-properties` — ważna korekta

Pierwotny wniosek ("nie da się bez forka kompilatora") był **za kategoryczny**.
Kompilator ma udokumentowaną flagę `-Xoverride-konan-properties`, która
pozwala nadpisać właściwości konfiguracyjne (CPU, cechy CPU, triple, flagi
clang) dla **już zaakceptowanej** nazwy targetu — nie trzeba rejestrować
nowej nazwy, wystarczy "przejąć" istniejącą (`linux_arm32_hfp`).

**Empirycznie potwierdzone**: `konanc -target linux_arm32_hfp
-Xoverride-konan-properties="targetCpu.linux_arm32_hfp=cortex-m0plus;..."
-produce static` **kończy się sukcesem** i produkuje realny plik
`libhellostatic.a` z kodem obiektowym w formacie `elf32-littlearm`, EABI
(nie glibc) — LLVM faktycznie wygenerował kod maszynowy dla Cortex-M0+/
Thumb-1, nie tylko przyjął flagę bez efektu. Runtime.bc (LLVM bitcode, więc
przenośny IR) zostaje poprawnie dolinkowany mimo ostrzeżenia o niezgodności
triple.

**Ale** — analiza niezdefiniowanych symboli w wynikowym pliku (134 symbole)
ujawnia prawdziwą skalę problemu, inną niż "kompilator tego nie potrafi":

| Kategoria | Liczba | Ocena wykonalności na RP2040 |
|---|---|---|
| `__aeabi_*` (libgcc) | 35 | ✅ Trywialne — dostarcza sam `arm-none-eabi-gcc` |
| `pthread_*` (wątki OS) | 21 | ❌ RP2040 nie ma OS-owych wątków — runtime K/N (napisany w C++) zakłada `std::thread`/`pthread_*` do swojego schedulera GC |
| libstdc++/C++ exceptions/RTTI (`_Z*`, `__cxa_*`, `_Unwind_*`) | 44 | ⚠️ Do zrobienia, ale istnieją porty libstdc++ dla embedded (np. w Pico SDK po części) |
| `mmap`/`munmap` | 2 | ❌ **RP2040 nie ma MMU** — mapowanie pamięci wirtualnej jest fizycznie niemożliwe, nie tylko niezaimplementowane |

## Zrewidowana przyczyna źródłowa

To nie jest (tylko) problem "zamkniętej listy targetów w kompilatorze" —
`-Xoverride-konan-properties` to obchodzi. Prawdziwy, twardszy problem leży
**w runtime K/N**: jest napisany w C++ i zaprojektowany wokół modelu
wielowątkowego z systemowymi wątkami (`pthread`) i alokatorem opartym o
`mmap` (wirtualna pamięć). RP2040 (Cortex-M0+) nie ma MMU — `mmap` w sensie
POSIX jest tam fizycznie niewykonalny, nie tylko brakujący. Zbudowanie
działającego runtime'u wymagałoby napisania zamienników `pthread_*`
(np. przez współpracujący scheduler na jednym rdzeniu lub FreeRTOS) i
alokatora bez `mmap` (statyczna pula pamięci) — to jest **port runtime'u**,
nie "fork kompilatora register target", ale wciąż realny, wielotygodniowy
projekt inżynierski, nie coś do zrobienia w ramach PoC.

Ticket KT-44498 (`research.md` § 1) pozostaje otwarty, bez przypisania i
bez planowanej wersji — oficjalne wsparcie nie jest w drodze.

## Rekomendacja

**Nie kontynuować Fazy 4 (US2 — plugin) w obecnym kształcie**, dopóki
fundamentalne założenie architektoniczne (Kotlin/Native na bare-metal
Cortex-M) nie zostanie potwierdzone jako wykonalne end-to-end (włącznie z
działającym runtime na sprzęcie) lub świadomie zaakceptowane jako projekt
dużo większy niż "Faza 0/1: PoC + minimalny plugin". To wymaga decyzji
użytkownika.

**Ważna zmiana względem pierwszej rundy**: nie trzeba forkować/rekompilować
kompilatora, żeby uzyskać codegen dla Cortex-M — `-Xoverride-konan-properties`
to załatwia. Prawdziwa bariera to **port runtime'u K/N** (zastąpienie
`pthread_*`/`mmap` odpowiednikami działającymi bez OS/MMU) — mniejszy
projekt niż fork kompilatora, ale wciąż wielotygodniowy/wielomiesięczny, nie
"spike".

Możliwe kierunki (do decyzji, nie zrealizowane w ramach tego spike'u):
1. **Port runtime'u K/N na bare-metal** (bez forka kompilatora) — zastąpić
   `pthread_*` współpracującym schedulerem/FreeRTOS i zaimplementować
   alokator bez `mmap` (statyczna pula), używając `-Xoverride-konan-properties`
   do retargetingu codegen. Wielotygodniowy projekt R&D, wysokie ryzyko
   (nieznane, ile jeszcze zależności "wypłynie" po podłączeniu runtime'u),
   ale mniejszy niż fork kompilatora.
2. ~~**Zmiana architektury**: pośredni artefakt LLVM/C~~ — **ZBADANE i
   ODRZUCONE (2026-07-03)**. `-produce bitcode` jest jawnie wyłączone w
   2.4.0 (`error: Bitcode output kind is obsolete`). `-Xsave-llvm-ir-after`
   działa tylko w ramach kompilacji dla już wybranego, wspieranego targetu
   — nie omija wymogu `-target`. Decydujący dowód: `runtime.bc` (GC,
   alokator, obsługa wyjątków K/N) jest prekompilowany **per-target** i
   istnieje wyłącznie dla wspieranych targetów — nie ma generycznej wersji.
   Nawet wydobyty LLVM IR byłby zlinkowany z runtime'em dla hostowanego OS
   i nie dałby się dalej skompilować pod bare-metal — ta sama przyczyna
   źródłowa co brak wsparcia runtime'u (opcja 1: pthread/mmap).
3. **Zawężenie zakresu projektu**: Kotlin/Native tylko na hostowany Linux
   ARM (np. Raspberry Pi pełnoprawny SBC, nie mikrokontroler Pico) —
   zmienia fundamentalnie cel projektu z `ROADMAP.md`, ale unika portu
   runtime'u całkowicie (hostowany Linux ma prawdziwe `pthread`/`mmap`).
4. **Fork i rekompilacja kompilatora** (rejestracja natywnego
   `KonanTarget` zamiast retargetingu przez `-Xoverride-konan-properties`)
   — opcja maksymalna, dająca pełną kontrolę, ale największy nakład
   (miesiące, wysokie ryzyko, wymaga też portu runtime'u jak w opcji 1).
   Racjonalna tylko jeśli opcja 1 okaże się niewystarczająca (np. limity
   `-Xoverride-konan-properties` ujawnione dopiero głębiej w spike'u).

**To nie jest decyzja, którą mogę podjąć autonomicznie** — zmienia
fundamentalne założenia `ROADMAP.md` i konstytucji projektu.
