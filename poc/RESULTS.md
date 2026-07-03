# PoC Results — Bramka go/no-go (T013)

**Data**: 2026-07-03 (runda 4 — ZAMKNIĘTA)

## Werdykt: **PEŁNY SUKCES — POTWIERDZONE NA FIZYCZNYM SPRZĘCIE**

**T013 wykonane na fizycznym Raspberry Pi Pico W**: po wgraniu
`kblink.uf2` dioda pokazała 5 błysków diagnostycznych (boot/crt0 OK), a
następnie ciągłe mruganie 250ms sterowane z pętli w **kodzie Kotlin**
(`Main.kt` → cinterop → CYW43). Kotlin/Native działa na bare-metal RP2040.

**Ważna lekcja sprzętowa (runda 4)**: pierwsza próba na sprzęcie
"nie działała", bo build był dla `PICO_BOARD=pico`, a płytka to **Pico W** —
tam dioda NIE wisi na GPIO25, tylko na chipie radiowym CYW43 (WL_GPIO0,
przez `cyw43_arch_gpio_put`, biblioteka `pico_cyw43_arch_none`). Binarki
prawdopodobnie działały od początku, machając niepodłączonym pinem. Wniosek
dla pluginu: rozróżnienie wariantów `_w` (FR-006) dotyczy nie tylko
cinterop WiFi, ale już samego sterowania LED — `BoardVariant.hasWifi` musi
wpływać na domyślny mechanizm LED w przykładach/szablonach.

| Krok | Status | Dowód |
|---|---|---|
| T007 — toolchain ARM + Pico SDK 2.2.0 | ✅ Sukces | `poc/SETUP.md` |
| T008 — kompilator Kotlin/Native 2.4.0 | ✅ Sukces (po korekcie URL) | `poc/SETUP.md` |
| T009 — nowy custom target (nazwa nierozpoznana) | ❌ Porażka (obejście znalezione niżej) | `poc/konan-target-spike.md` |
| T009b — retargeting przez `-Xoverride-konan-properties` | ✅ Codegen działa | `poc/konan-target-spike.md` § "Runda 2" |
| T009c — **patch atrybutów w runtime `.bc` + shim C + lld + linker script** | ✅ **Pełny link działa** | `poc/konan-target-spike.md` § "Runda 3" |
| T010 — cinterop (`pico.klib` z wrapperami GPIO) | ✅ Sukces (wymaga tych samych override'ów co konanc) | `poc/interop/` |
| T011 — kompilacja blink Kotlin → ELF | ✅ Sukces — `kblink.elf`, 340 funkcji Kotlin, czysty Thumb-1 (entry 0x100001e9) | `poc/blink/` |
| T012 — konwersja ELF → UF2 | ✅ Sukces — `kblink.uf2` (544 KB), poprawnie czytany przez `picotool info` | `poc/blink/build-k/` |
| T013 — flash na fizyczne urządzenie i wizualna weryfikacja diody | ✅ **Sukces (2026-07-03)** — Pico W, 5 błysków diag + mruganie 250ms z kodu Kotlin | potwierdzenie użytkownika; wymagany rebuild pod `PICO_BOARD=pico_w` (LED przez CYW43) |

## Działający pipeline (runda 3)

```
Main.kt ──konanc──▶ libkotlinapp.a ──┐
  (-target linux_arm32_hfp           │
   -Xoverride-konan-properties=      ├──CMake+pico-sdk+lld──▶ kblink.elf ──picotool──▶ kblink.uf2
     cortex-m0plus/thumbv6m/static   │
   -Xbinary=gc=noop -Xallocator=std) │
pico.klib (cinterop, te same         │
  override'y) ───────────────────────┤
kopico_shim.c (~150 linii stubów) ───┤
wrapper.c (mostek GPIO + main) ──────┘
```

Niezbędne komponenty (wszystkie w `poc/blink/`):
1. **Patch atrybutów `.bc`**: pliki runtime w `konan/targets/linux_arm32_hfp/native/*.bc`
   mają wkompilowane per-funkcyjne atrybuty `target-cpu="arm1176jzf-s"`/`-thumb-mode`,
   które wygrywają z triple i generują kod ARM-mode (nielegalny na Armv6-M).
   Jednorazowy rewrite: `clang -x ir → sed atrybutów → clang -c -emit-llvm`
   (backup w `native.bak`). To samo `-Xoverride-konan-properties` musi być
   podane też do `cinterop` (bridge klib też niesie atrybuty).
2. **`staticLibraryRelocationMode=static`** w override'ach — bez tego kod jest
   PIC i tworzy `.got`, którego linker script Pico nie umieszcza we flashu.
3. **Shim C** (`kopico_shim.c`, ~150 linii): pthread no-op (single thread),
   mmap → statyczna arena 64KB, `__aeabi_read_tp` (naked asm) + `__tls_get_addr`,
   stuby `std::condition_variable` (libstdc++ arm-none-eabi jest single-thread),
   `stdout`/`stderr` jako symbole (w newlib to makra), `sleep`/`dladdr`/`syscall`.
4. **Linker: `ld.lld`** (z dystrybucji LLVM K/N) zamiast `ld.bfd` — bfd nie
   trawi relokacji Thumb z obiektów LLVM; 3-liniowy wrapper filtruje
   flagę `--no-warn-rwx-segments`, której lld nie zna.
5. **Linker script**: kopia `memmap_default.ld` + `*(.got*)` do FLASH.

## Znane ograniczenia zbudowanego artefaktu

- `gc=noop` — pamięć alokowana, nigdy nie zwalniana; dla blink/prostych
  aplikacji OK, dla długożyjących wymaga zbadania `gc=stms` + shim wątków.
- Runtime zajmuje ~266KB flash (z 2MB) i ~72KB statycznego RAM (z 264KB) —
  akceptowalne, ale optymalizacja rozmiaru to osobny temat (Faza 4 ROADMAP).
- Patch `.bc` modyfikuje dystrybucję kompilatora — plugin będzie musiał robić
  to samo automatycznie w swoim cache (deterministyczny, jednorazowy krok).
- `linux_arm32_hfp` jest deprecated — wersja K/N przypięta konstytucją (2.4.0),
  więc stabilne, ale migracja na nowszą wersję K/N wymaga re-walidacji.
- **Nieprzetestowane na sprzęcie** — statyczna poprawność (Thumb-1, ABI,
  layout pamięci) zweryfikowana narzędziowo, ale runtime init (konstruktory
  globalne K/N, inicjalizacja stdlib) może jeszcze zaskoczyć na żywym chipie.

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

## Rekomendacja (po rundzie 3)

Wcześniejsze rekomendacje (runda 1: "wymaga forka kompilatora", runda 2:
"wymaga wielotygodniowego portu runtime'u") okazały się zbyt pesymistyczne.
Runda 3 pokazała, że przewidywany "port runtime'u" sprowadził się do
**~150 linii stubów C + patch atrybutów `.bc` + lld + poprawka linker
scriptu** — wykonane w całości w ramach tego spike'u, z działającym UF2 na
końcu. Historia rund 1-2 powyżej zachowana celowo jako zapis procesu
dochodzenia do rozwiązania.

**T013 wykonane pozytywnie (2026-07-03, Pico W)** — bramka go/no-go
zamknięta. **Faza 4 (US2 — plugin) jest odblokowana.** Zadania pluginu
(`CompileNativeTask`, provisioning, `Uf2Writer`) mają kompletny,
zweryfikowany na sprzęcie przepis techniczny: override'y konan.properties,
patch `.bc` w cache narzędzi, generacja shim/wrapper, lld + wrapper
filtrujący flagi, linker script z `.got` we FLASH, oraz routing LED przez
CYW43 dla wariantów `_w`.
