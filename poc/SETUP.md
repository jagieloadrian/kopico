# PoC Setup Log (T007-T008)

Performed 2026-07-03, Linux x86_64.

## ARM Toolchain (T007)

Source: xPack ARM Embedded GCC (`research.md` § 5), latest release
verified via the GitHub API: `v15.2.1-1.1`.

```bash
curl -L -o gcc.tar.gz \
  https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v15.2.1-1.1/xpack-arm-none-eabi-gcc-15.2.1-1.1-linux-x64.tar.gz
tar xzf gcc.tar.gz
```

Verified: `poc/toolchain/xpack-arm-none-eabi-gcc-15.2.1-1.1/bin/arm-none-eabi-gcc --version`
→ `arm-none-eabi-gcc (xPack GNU Arm Embedded GCC x86_64) 15.2.1 20251203`.

## Pico SDK (T007)

```bash
git clone --branch 2.2.0 --depth 1 --recurse-submodules --shallow-submodules \
  https://github.com/raspberrypi/pico-sdk.git pico-sdk
```

Submodules cloned correctly (`btstack`, `cyw43-driver`, `lwip`,
`mbedtls` + nested `mbedtls-framework`, `tinyusb`). Version confirmed
in `pico_sdk_version.cmake`: `2.2.0`.

## Kotlin/Native 2.4.0 compiler (T008)

**Discrepancy with `research.md`**: the URL
`download.jetbrains.com/kotlin/native/builds/releases/...` returns
`404`. The actual source is GitHub Releases:

```bash
curl -L -o kn.tar.gz \
  https://github.com/JetBrains/kotlin/releases/download/v2.4.0/kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz
tar xzf kn.tar.gz
```

Verified: `bin/konanc -version` → `Kotlin/Native: 2.4.0`. `research.md`
updated with the correct URL.

## Next steps

See `poc/konan-target-spike.md` (T009) — the custom target attempt
**failed** empirically, which blocks T010-T013 in their current form.
See `poc/RESULTS.md` for the go/no-go gate verdict.
