# Spike: custom Kotlin/Native target dla RP2040 (T009)

**Data**: 2026-07-03
**Kompilator**: Kotlin/Native 2.4.0 (linux-x86_64 prebuilt,
`kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz`, pobrany bezpośrednio z
GitHub Releases JetBrains/kotlin — patrz `research.md` § 3, poprawka URL)

## Kroki

1. Sprawdzono listę wbudowanych targetów: `konanc -list-targets`

   ```
   linux_x64 (default)
   linux_arm32_hfp (deprecated)
   linux_arm64
   mingw_x64
   android_x86
   android_x64
   android_arm32
   android_arm64
   ```

   Brak jakiegokolwiek targetu bare-metal/freestanding — zgodne z ustaleniem
   `research.md` § 1 (oficjalna lista wspieranych targetów).

2. Zbadano `konan/konan.properties` (796 linii) w dystrybucji. Plik
   definiuje **konfigurację toolchaina** (ścieżki do gcc/clang, sysroot,
   flagi linkera, cechy CPU) dla kluczy w formacie
   `<własność>.<nazwa_targetu>`, np. `targetTriple.linux_arm32_hfp`,
   `targetCpu.linux_arm32_hfp`. Nie ma tam żadnego mechanizmu deklarowania
   *nowej* nazwy targetu — właściwości są tylko odczytywane dla nazw, które
   kompilator już rozpoznaje.

3. Próba kompilacji z nieistniejącym targetem:

   ```
   $ konanc -target thumbv6m-none-eabi hello.kt -o hello
   exception: java.lang.IllegalStateException: Unknown target: thumbv6m-none-eabi
       at org.jetbrains.kotlin.native.NativeFirstStageCompilationConfigKt.createFirstStageCompilationConfig(...)
   ```

   To samo dla `-target armv6-m`. Błąd pochodzi z
   `NativeFirstStageCompilationConfig.kt` — walidacja nazwy targetu
   następuje względem zamkniętego enuma `KonanTarget` wkompilowanego w JAR
   kompilatora, **przed** jakąkolwiek konsultacją `konan.properties`.

## Wniosek

**Rozszerzenie `konan.properties` o custom target NIE DZIAŁA** ze stockowym
kompilatorem Kotlin/Native 2.4.0 — to empirycznie obalona hipoteza z
`research.md` § 1 (community spike bez potwierdzonego sukcesu — teraz mamy
bezpośredni dowód dlaczego: to nie jest kwestia konfiguracji, tylko
twardego ograniczenia w kodzie kompilatora).

Jedyna droga do custom bare-metal targetu wymagałaby:
- forka/patcha kompilatora Kotlin/Native (dodanie wpisu do `KonanTarget`,
  rekompilacja `native/kotlin-native` z JetBrains/kotlin) — ogromny nakład,
  poza zakresem "spike'u"; oraz
- napisania/zaadaptowania runtime'u K/N (GC, alokator, model pamięci) pod
  środowisko bez OS — obecny runtime zakłada `mmap`/wątki/hostowany libc.

**Werdykt bramki go/no-go (patrz `poc/RESULTS.md`): PORAŻKA w obecnym
zakresie spike'u.** Wymaga eskalacji do użytkownika przed kontynuacją
T010-T013 (zależnych od działającego custom targetu) i przed Fazą 4 (US2).

## Runda 2 (2026-07-03): `-Xoverride-konan-properties` — alternatywa bez nowej nazwy targetu

Sprawdzono, czy pośredni artefakt LLVM/C omija problem (nie — patrz
`poc/RESULTS.md` opcja 2, `-produce bitcode` jawnie wyłączone w 2.4.0). Przy
tej okazji odkryto flagę `-Xoverride-konan-properties=key=val;...`
(`konanc -X`), pozwalającą nadpisać właściwości `konan.properties` z CLI
dla **istniejącej, zaakceptowanej** nazwy targetu — bez potrzeby rejestracji
nowej nazwy.

### Eksperyment: przejęcie `linux_arm32_hfp` pod Cortex-M0+

```bash
OVERRIDES="targetCpu.linux_arm32_hfp=cortex-m0plus;\
targetCpuFeatures.linux_arm32_hfp=+strict-align,-neon,-vfp2,-vfp3,-vfp4;\
targetTriple.linux_arm32_hfp=thumbv6m-none-eabi;\
clangFlags.linux_arm32_hfp=-cc1 -mfloat-abi soft -emit-obj -disable-llvm-optzns -x ir"

konanc -target linux_arm32_hfp \
  -Xoverride-konan-properties="$OVERRIDES" \
  -produce static hello.kt -o hellostatic
```

**Wynik**: sukces (z ostrzeżeniem o niezgodności triple między naszym
kodem `thumbv6m-unknown-none-eabihf` a `runtime.bc`, który wciąż deklaruje
`armv6kz-unknown-linux-gnueabihf` — LLVM i tak zlinkował moduły, bo
`runtime.bc` to przenośny LLVM IR, nie gotowy kod maszynowy). Powstał
`libhellostatic.a` zawierający **realny obiekt ELF32 `elf32-littlearm`,
EABI** (nie glibc-owy ABI) — zweryfikowane przez
`arm-none-eabi-readelf -h`.

Wcześniejsza próba z `-produce dynamic` dochodzi aż do etapu linkowania i
dopiero tam pada (`ld.bfd: uses VFP register arguments... does not` —
niezgodność hard/soft-float między naszym kodem a glibc-ową biblioteką
współdzieloną dla tego targetu) — to potwierdza, że **codegen LLVM dla
Cortex-M0+/Thumb-1 faktycznie działa**, problem jest wyłącznie w
finalnym linkowaniu/runtime, nie w samym tłumaczeniu Kotlin → maszynowy
kod ARM.

### Analiza wynikowego pliku obiektowego

`arm-none-eabi-nm -u libhellostatic.a.o` → 134 niezdefiniowane symbole:
35 `__aeabi_*` (libgcc, trywialne), 21 `pthread_*`, 44
libstdc++/C++-exceptions/RTTI, 2 `mmap`/`munmap`. Runtime K/N (C++, GC
wielowątkowy) wymaga prawdziwych wątków systemowych i mapowania pamięci
wirtualnej — **RP2040 nie ma MMU**, więc `mmap` w sensie POSIX jest
fizycznie niewykonalny na tym chipie, nie tylko brakujący.

### Zrewidowany wniosek

Teza "trzeba forkować kompilator" była za kategoryczna —
`-Xoverride-konan-properties` daje działający codegen bez forka. Prawdziwa
bariera przesuwa się z "kompilator nie umie" na "**runtime K/N wymaga
portu** na środowisko bez wątków OS i bez MMU" — mniejszy, ale wciąż
wielotygodniowy projekt. Pełna analiza opcji: `poc/RESULTS.md`.

## Runda 3 (2026-07-03): pełny pipeline Kotlin → UF2 — DZIAŁA

Kontynuacja rundy 2 doprowadziła do kompletnego, działającego builda.
Napotkane i rozwiązane problemy, w kolejności:

1. **`ld.bfd`: "Unknown destination type (ARM/Thumb)" / R_ARM_JUMP24**.
   Pliki runtime `konan/targets/linux_arm32_hfp/native/*.bc` mają
   wkompilowane per-funkcyjne atrybuty LLVM `"target-cpu"="arm1176jzf-s"`,
   `"target-features"="...,-thumb-mode,..."`, które **wygrywają z triple** i
   generują kod ARM-mode — nielegalny na Armv6-M (Thumb-only). Fix:
   jednorazowy patch wszystkich `.bc` (backup w `native.bak`):
   ```bash
   clang -target thumbv6m-unknown-none-eabi -x ir f.bc -S -emit-llvm -o - \
     | sed -e 's/"target-cpu"="arm1176jzf-s"/"target-cpu"="cortex-m0plus"/g' \
           -e 's/"target-features"="[^"]*"/"target-features"="+strict-align,+thumb-mode,+soft-float,-neon,..."/g' \
     | clang -target thumbv6m-unknown-none-eabi -x ir - -c -emit-llvm -o f.bc
   ```
   Uwaga: `clang -x ir` bez `-target` nadpisuje triple hostem (x86_64) —
   trzeba podać jawnie. Te same override'y muszą iść do `cinterop`
   (bridge w klib też niesie atrybuty ARM).
2. **`ld.bfd` nadal nie trawi relokacji Thumb z obiektów LLVM** → link przez
   `ld.lld` z dystrybucji LLVM K/N (`-fuse-ld=lld -B<dir>`); 3-liniowy
   wrapper filtruje flagę `--no-warn-rwx-segments` (bfd-only).
3. **PIC → `.got` w RAM bez LMA** (picotool: "memory contents for
   uninitialized memory"). Fix: `staticLibraryRelocationMode.linux_arm32_hfp=static`
   w override'ach + kopia `memmap_default.ld` z `*(.got*)` w sekcji FLASH
   (wpisy GOT rozwiązane statycznie = read-only).
4. **`__aeabi_read_tp`** (local-exec TLS po przejściu na static) — naked-asm
   stub zwracający statyczny blok (specjalne ABI: wolno ruszyć tylko r0).
5. **`std::condition_variable`** — libstdc++ w arm-none-eabi jest
   single-thread i nie ma tych symboli; no-op stuby pod zmanglowanymi
   nazwami (poprawne identyfikatory C).
6. **`stdout`/`stderr`** — w newlib to makra, nie symbole; osobny TU
   definiuje globale i konstruktor podstawia realne strumienie newlib.

**Wynik**: `kblink.elf` (340 funkcji Kotlin, entry point Thumb 0x100001e9,
`kfun:#main` zdisasemblowany jako czysty Thumb-1) → `kblink.uf2` (544 KB,
`picotool info` czyta poprawnie, family rp2040, binarka
0x10000000-0x100426d0). Kompletny przepis reprodukcji: `poc/blink/`
(CMakeLists.txt, wrapper.c, kopico_shim.c, kopico_stdio_globals.c,
memmap_kopico.ld, lld-wrap/).
