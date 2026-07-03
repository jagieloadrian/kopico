# PoC Setup Log (T007-T008)

Wykonane 2026-07-03, Linux x86_64.

## Toolchain ARM (T007)

Źródło: xPack ARM Embedded GCC (`research.md` § 5), najnowszy release
zweryfikowany przez GitHub API: `v15.2.1-1.1`.

```bash
curl -L -o gcc.tar.gz \
  https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v15.2.1-1.1/xpack-arm-none-eabi-gcc-15.2.1-1.1-linux-x64.tar.gz
tar xzf gcc.tar.gz
```

Zweryfikowano: `poc/toolchain/xpack-arm-none-eabi-gcc-15.2.1-1.1/bin/arm-none-eabi-gcc --version`
→ `arm-none-eabi-gcc (xPack GNU Arm Embedded GCC x86_64) 15.2.1 20251203`.

## Pico SDK (T007)

```bash
git clone --branch 2.2.0 --depth 1 --recurse-submodules --shallow-submodules \
  https://github.com/raspberrypi/pico-sdk.git pico-sdk
```

Submoduły sklonowane poprawnie (`btstack`, `cyw43-driver`, `lwip`,
`mbedtls` + zagnieżdżony `mbedtls-framework`, `tinyusb`). Wersja
potwierdzona w `pico_sdk_version.cmake`: `2.2.0`.

## Kompilator Kotlin/Native 2.4.0 (T008)

**Rozbieżność z `research.md`**: URL `download.jetbrains.com/kotlin/native/builds/releases/...`
zwraca `404`. Rzeczywiste źródło to GitHub Releases:

```bash
curl -L -o kn.tar.gz \
  https://github.com/JetBrains/kotlin/releases/download/v2.4.0/kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz
tar xzf kn.tar.gz
```

Zweryfikowano: `bin/konanc -version` → `Kotlin/Native: 2.4.0`. `research.md`
zaktualizowany o poprawny URL.

## Dalsze kroki

Patrz `poc/konan-target-spike.md` (T009) — próba custom targetu
**nie powiodła się** empirycznie, co blokuje T010-T013 w obecnym kształcie.
Patrz `poc/RESULTS.md` dla werdyktu bramki go/no-go.
