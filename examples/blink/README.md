# examples/blink

A minimal project blinking the onboard LED, built with the
`com.anjo.kopico` plugin.

## Usage

1. Publish the plugin locally (from the repo root directory):

   ```bash
   ./gradlew publishToMavenLocal
   ```

2. Set `board` in `build.gradle.kts` **to match the board you have** — this
   matters, because on `_w` variants (Pico W / Pico 2 W) the LED does not
   hang off GPIO25, but off the CYW43 WiFi chip. Building for the wrong
   board will toggle an unconnected pin and the LED won't blink (a lesson
   from the PoC, `poc/RESULTS.md`):

   | Board | `board` |
   |---|---|
   | Raspberry Pi Pico | `pico` |
   | Raspberry Pi Pico W | `pico_w` |
   | Raspberry Pi Pico 2 | `pico2` |
   | Raspberry Pi Pico 2 W | `pico2_w` |

   The code in `Main.kt` is shared across all variants —
   `kopico_default_led_pin()` itself routes LED operations to GPIO or CYW43.

3. Build (the first run downloads the toolchain — requires network):

   ```bash
   ../../gradlew -p . build
   ```

4. Flash `build/kopico/blink.uf2` onto the board in BOOTSEL mode (hold the
   BOOTSEL button while connecting USB, copy the file to the `RPI-RP2` drive).
