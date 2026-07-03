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
