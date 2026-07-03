# examples/blink

Minimalny projekt mrugający wbudowaną diodą LED, budowany pluginem
`com.anjo.kopico`.

## Użycie

1. Opublikuj plugin lokalnie (z katalogu głównego repo):

   ```bash
   ./gradlew publishToMavenLocal
   ```

2. Ustaw `board` w `build.gradle.kts` **zgodnie z posiadaną płytką** — to
   istotne, bo na wariantach `_w` (Pico W / Pico 2 W) dioda nie wisi na
   GPIO25, tylko na chipie WiFi CYW43. Build dla złej płytki będzie machał
   niepodłączonym pinem i dioda nie mrugnie (lekcja z PoC, `poc/RESULTS.md`):

   | Płytka | `board` |
   |---|---|
   | Raspberry Pi Pico | `pico` |
   | Raspberry Pi Pico W | `pico_w` |
   | Raspberry Pi Pico 2 | `pico2` |
   | Raspberry Pi Pico 2 W | `pico2_w` |

   Kod w `Main.kt` jest wspólny dla wszystkich wariantów —
   `kopico_default_led_pin()` sam kieruje operacje LED na GPIO albo CYW43.

3. Zbuduj (pierwsze uruchomienie pobiera toolchain — wymaga sieci):

   ```bash
   ../../gradlew -p . build
   ```

4. Wgraj `build/kopico/blink.uf2` na płytkę w trybie BOOTSEL (przytrzymaj
   przycisk BOOTSEL przy podłączaniu USB, skopiuj plik na dysk `RPI-RP2`).
