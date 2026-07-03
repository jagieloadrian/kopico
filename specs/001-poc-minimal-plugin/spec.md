# Feature Specification: PoC & Minimalny Plugin Kotlin/Native dla Pico

**Feature Branch**: `001-poc-minimal-plugin`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "--phase 0,1" — odwołanie do Fazy 0 (Research & PoC)
i Fazy 1 (Podstawowy Gradle Plugin) z `ROADMAP.md`: potwierdzenie wykonalności
pisania kodu Kotlin/Native dla Raspberry Pi Pico oraz dostarczenie minimalnego,
działającego pluginu Gradle `com.anjo.kopico`.

## Clarifications

### Session 2026-07-02

- Q: Czy plugin ma weryfikować/wymuszać wersję Pico SDK wskazaną przez `sdkPath`? → A: Minimalna wersja SDK (`>= 2.2.0`, najnowsza stabilna wersja Pico SDK w chwili specyfikacji) — plugin akceptuje nowsze, odrzuca starsze.
- Q: Czy plugin ma jawnie sprawdzać dostępność `arm-none-eabi-gcc` (i pokrewnych narzędzi) przed buildem? → A: Tak — plugin jawnie sprawdza obecność toolchaina ARM przed kompilacją i zwraca czytelny błąd, jeśli brak.
- Q: Jakie narzędzia plugin ma potrafić samodzielnie pobrać/dostarczyć, żeby developer mógł z nich skorzystać bez ręcznej instalacji? → A: Pico SDK + toolchain ARM + picotool/OpenOCD (pełny zestaw, łącznie z narzędziami do flashowania/debugu).
- Q: Jaka ma być strategia pobierania narzędzi przez plugin? → A: Automatyczne pobieranie z lokalnym cache i przypięciem wersji — pobiera raz do katalogu cache, kolejne buildy używają cache bez sieci.
- Q: Czy SC-005 ("czyste środowisko CI") ma wymagać faktycznego, minimalnego workflow CI już w tej fazie, czy wystarczy lokalna symulacja czystego środowiska? → A: Wymagany minimalny, faktyczny workflow CI (np. jeden job GitHub Actions uruchamiający build) już w tej fazie — pełne CI/CD (multi-job, release automation itp.) pozostaje w Fazie 5.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Zweryfikowanie wykonalności podejścia (Priority: P1)

Jako inżynier budujący plugin, chcę mieć potwierdzony, działający dowód
koncepcji (PoC) pokazujący, że kod Kotlin/Native może zostać skompilowany i
uruchomiony na Raspberry Pi Pico (RP2040) przy użyciu Pico SDK, zanim
zainwestuję czas w budowę pełnego pluginu Gradle.

**Why this priority**: Bez potwierdzenia wykonalności technicznej (custom
target Kotlin/Native + cinterop z Pico SDK + generowanie UF2) całe dalsze
planowanie pluginu jest ryzykowne — to fundament, na którym stoi cały projekt.

**Independent Test**: Można zweryfikować niezależnie, kompilując przykładowy
program "blink" napisany w Kotlin/Native, generując z niego plik UF2 i
wgrywając go na fizyczne urządzenie Pico, obserwując mrugającą diodę LED.

**Acceptance Scenarios**:

1. **Given** zainstalowany Pico SDK i toolchain ARM, **When** deweloper
   kompiluje ręcznie skonfigurowany projekt Kotlin/Native z cinterop do
   `pico_stdlib`/`hardware_gpio`, **Then** kompilacja kończy się sukcesem i
   powstaje plik wykonywalny ELF.
2. **Given** skompilowany plik ELF, **When** deweloper generuje z niego UF2,
   **Then** plik UF2 daje się skopiować na urządzenie Pico w trybie BOOTSEL.
3. **Given** wgrany UF2 z programem blink, **When** urządzenie Pico zostanie
   zrestartowane, **Then** wbudowana dioda LED mruga w oczekiwanym rytmie.

---

### User Story 2 - Minimalna konfiguracja pluginu przez deweloperów (Priority: P2)

Jako deweloper chcący pisać kod na Pico w Kotlinie, chcę zainstalować plugin
Gradle `com.anjo.kopico`, skonfigurować w nim docelową płytkę i ścieżkę do
Pico SDK, i zbudować przykładowy projekt blink bez ręcznego pisania
konfiguracji cinterop czy toolchaina.

**Why this priority**: To pierwszy realny, publikowalny artefakt projektu —
minimalny plugin, który zamienia manualny PoC z User Story 1 w powtarzalne
narzędzie dla innych deweloperów.

**Independent Test**: Można przetestować niezależnie, tworząc nowy projekt
Gradle, dodając plugin `com.anjo.kopico`, deklarując `board = "pico"` i
`sdkPath`, i uruchamiając build, który generuje działający plik UF2 bez
dodatkowej ręcznej konfiguracji cinterop.

**Acceptance Scenarios**:

1. **Given** nowy projekt Gradle z zastosowanym pluginem `com.anjo.kopico`,
   **When** deweloper ustawia `board = "pico"` oraz `sdkPath` we extension,
   **Then** plugin automatycznie rejestruje odpowiedni target Kotlin/Native i
   konfiguruje cinterop dla kluczowych bibliotek Pico SDK (`pico_stdlib`,
   `hardware_gpio`, `hardware_pwm`).
2. **Given** skonfigurowany projekt z przykładowym kodem blink w Kotlinie,
   **When** deweloper uruchamia build pluginu, **Then** build kończy się
   sukcesem i produkuje plik UF2 gotowy do wgrania na urządzenie.
3. **Given** deweloper zmienia `board` na `"pico_w"`, **When** uruchamia
   build ponownie, **Then** plugin dodaje dodatkowy cinterop dla CYW43 bez
   potrzeby zmiany innej konfiguracji.
4. **Given** deweloper nie ustawił `sdkPath` ani nie ma lokalnie
   zainstalowanego Pico SDK, toolchaina ARM, picotool ani OpenOCD, **When**
   uruchamia build po raz pierwszy, **Then** plugin automatycznie pobiera i
   lokalnie cache'uje wymagane narzędzia (w przypiętych wersjach) bez
   interakcji użytkownika, a build kończy się sukcesem.
5. **Given** narzędzia zostały już raz pobrane i są w lokalnym cache,
   **When** deweloper uruchamia kolejny build bez dostępu do sieci, **Then**
   build kończy się sukcesem, korzystając wyłącznie z cache.
6. **Given** deweloper poda nieprawidłową wartość `board` (spoza obsługiwanego
   zestawu), **When** uruchamia build, **Then** otrzymuje czytelny komunikat
   błędu wskazujący nieprawidłową konfigurację, zamiast niejasnego fail
   buildu.

---

### Edge Cases

- Co się dzieje, gdy deweloper jawnie ustawił `sdkPath`, ale wskazuje on na
  nieistniejącą, niekompletną lub zbyt starą (< 2.2.0) kopię Pico SDK? (Auto-
  provisioning NIE nadpisuje jawnie podanej, błędnej ścieżki — plugin zgłasza
  czytelny błąd zamiast po cichu pobierać inną wersję; auto-pobieranie
  uruchamia się wyłącznie, gdy `sdkPath` nie jest ustawiony.)
- Jak system zachowuje się, gdy deweloper poda nieobsługiwaną wartość
  `board` (spoza `pico`/`pico_w`/`pico2`/`pico2_w`)?
- Co się dzieje, gdy automatyczne pobranie narzędzia (Pico SDK, toolchain
  ARM, picotool, OpenOCD) nie powiedzie się (brak sieci przy pierwszym
  użyciu, niezgodna suma kontrolna, niedostępny serwer)?
- Jak zachowuje się build, gdy PoC/plugin uruchamiany jest na hoście spoza
  Linuksa (poza zadeklarowanym zakresem CI z `ROADMAP.md`) — auto-
  provisioning dla innych OS jest poza zakresem tej specyfikacji?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System (PoC) MUSI wykazać, że kod napisany w Kotlin/Native,
  skompilowany pod custom target ARM (bez Kotlin Multiplatform), może
  wywoływać funkcje Pico SDK (np. `gpio_put`) przez cinterop.
- **FR-002**: System (PoC) MUSI wygenerować plik UF2 z skompilowanego
  programu Kotlin/Native, możliwy do wgrania na fizyczne urządzenie Pico.
- **FR-003**: Plugin MUSI udostępniać extension DSL pozwalający deweloperowi
  zadeklarować `board` (jedną z: `pico`, `pico_w`, `pico2`, `pico2_w`) i
  opcjonalnie `sdkPath` — jeśli `sdkPath` nie zostanie ustawiony, plugin MUSI
  automatycznie dostarczyć Pico SDK zgodnie z FR-013.
- **FR-004**: Plugin MUSI automatycznie rejestrować właściwy custom target
  Kotlin/Native (z odpowiednim triple: `thumbv6m-none-eabi` dla RP2040,
  `thumbv8m.main-none-eabi`/`eabihf` dla RP2350) na podstawie wybranego
  `board`.
- **FR-005**: Plugin MUSI automatycznie skonfigurować cinterop dla kluczowych
  bibliotek Pico SDK (`pico_stdlib`, `hardware_gpio`, `hardware_pwm`) bez
  ręcznej konfiguracji przez użytkownika.
- **FR-006**: Plugin MUSI, dla wariantów `board` kończących się na `_w`
  (`pico_w`, `pico2_w`), automatycznie dodawać cinterop dla
  `pico_cyw43_arch` (WiFi/CYW43); dla wariantów bez `_w` cinterop ten MUSI
  być pominięty.
- **FR-007**: Plugin MUSI umożliwiać zbudowanie przykładowego projektu
  "blink", kończące się wygenerowaniem pliku UF2 gotowego do wgrania.
- **FR-008**: Plugin MUSI zwracać czytelny, zrozumiały błąd konfiguracji, gdy
  `board` jest nieprawidłowy, gdy jawnie ustawiony `sdkPath` jest
  nieistniejący/niekompletny/zbyt stary, lub gdy automatyczne dostarczenie
  wymaganego narzędzia (FR-013) się nie powiedzie — zamiast nieczytelnego
  fail ze stack trace.
- **FR-011**: Plugin MUSI zapewniać, że używany Pico SDK ma wersję co
  najmniej `2.2.0` (najnowsza stabilna wersja Pico SDK w chwili tej
  specyfikacji): jeśli `sdkPath` jest jawnie ustawiony, plugin waliduje jego
  wersję i zgłasza błąd, gdy jest starsza niż wymagana lub nie da się jej
  odczytać; jeśli `sdkPath` nie jest ustawiony, plugin automatycznie
  dostarcza SDK w wersji `>= 2.2.0` zgodnie z FR-013.
- **FR-012**: Plugin MUSI zapewniać dostępność toolchaina ARM
  (`arm-none-eabi-gcc` i pokrewnych narzędzi): jeśli toolchain jest już
  dostępny w PATH lub w lokalnym cache pluginu, plugin go używa; w
  przeciwnym razie automatycznie go dostarcza zgodnie z FR-013, zamiast
  pozwalać, by brak toolchaina ujawnił się jako nieczytelny fail
  kompilatora.
- **FR-009**: Plugin MUSI być publikowalny lokalnie (Maven Local), tak aby
  mógł zostać użyty w osobnym przykładowym projekcie testowym.
- **FR-010**: Dokumentacja PoC MUSI opisywać ręczny proces konfiguracji
  (krok po kroku) leżący u podstaw automatyzacji dostarczanej przez plugin,
  tak aby przyszli kontrybutorzy rozumieli, co plugin robi "pod maską".
- **FR-013**: Plugin MUSI potrafić automatycznie pobrać i dostarczyć (bez
  ręcznej instalacji przez dewelopera) Pico SDK, toolchain ARM
  (`arm-none-eabi-gcc`), `picotool` oraz OpenOCD — w wersjach spełniających
  wymagania projektu (np. `>= 2.2.0` dla Pico SDK, zgodnie z FR-011).
  `picotool`/OpenOCD są dostarczane na potrzeby przyszłych zadań
  flash/debug (Faza 3 `ROADMAP.md`); w zakresie tej specyfikacji (Faza 0/1)
  wymagane jest tylko ich pobranie i cache'owanie, nie implementacja
  samych zadań `flash`/`debug`.
- **FR-014**: Automatyczne dostarczanie narzędzi (FR-013) MUSI korzystać z
  lokalnego cache z przypięciem wersji: połączenie sieciowe MUSI być
  wymagane wyłącznie przy pierwszym pobraniu danego narzędzia/wersji na
  danej maszynie; kolejne buildy MUSZĄ korzystać z cache bez dostępu do
  sieci.

### Key Entities

- **Board Variant**: reprezentuje wspieraną płytkę (`pico`, `pico_w`,
  `pico2`, `pico2_w`); niesie ze sobą przypisany triple targetu i informację,
  czy wspiera WiFi/CYW43.
- **Pico SDK Reference**: ścieżka/wersja lokalnej kopii Pico SDK dostarczonej
  przez dewelopera, z której plugin czerpie nagłówki do cinterop.
- **Build Artifact**: wynikowy plik UF2 (oraz pośredni ELF) będący efektem
  builda, gotowy do flashowania na urządzenie.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zespół potrafi zademonstrować działający program blink na
  fizycznym urządzeniu Pico (dioda mruga) w oparciu wyłącznie o kod
  Kotlin/Native, w ciągu maks. 2 tygodni od rozpoczęcia prac (zgodnie z
  szacunkiem Fazy 0 w `ROADMAP.md`).
- **SC-002**: Nowy deweloper, bez wcześniej zainstalowanego Pico SDK ani
  toolchaina ARM, jest w stanie od zera skonfigurować projekt z pluginem i
  zbudować działający UF2 dla płytki `pico` w mniej niż 15 minut (wliczając
  czas pierwszego automatycznego pobrania narzędzi), korzystając wyłącznie z
  dokumentacji README.
- **SC-003**: Zmiana docelowej płytki z `pico` na `pico_w` (i odwrotnie)
  wymaga zmiany tylko jednej linii konfiguracji (`board = ...`) — bez
  dodatkowych ręcznych kroków.
- **SC-004**: 100% z czterech wspieranych wariantów płytek (pico, pico_w,
  pico2, pico2_w) ma poprawnie przypisany triple targetu i logikę cinterop,
  weryfikowalne bez fizycznego dostępu do wszystkich urządzeń (np. przez
  testy konfiguracji pluginu).
- **SC-005**: Build przykładowego projektu blink kończy się sukcesem (bez
  błędów) za pierwszym uruchomieniem w faktycznym, zautomatyzowanym
  środowisku CI (jeden hostowany job na Linux), bez interaktywnej
  interwencji.
- **SC-006**: Po pierwszym pobraniu narzędzi (Pico SDK, toolchain ARM,
  picotool, OpenOCD) na danej maszynie, kolejne buildy tego samego projektu
  kończą się sukcesem bez dostępu do sieci, korzystając wyłącznie z
  lokalnego cache.

## Assumptions

- Deweloper korzystający z pluginu NIE musi mieć wcześniej zainstalowanego
  Pico SDK, toolchaina ARM, `picotool` ani OpenOCD — plugin dostarcza je
  automatycznie (FR-013/FR-014). Ręczna instalacja pozostaje możliwa (przez
  jawne ustawienie `sdkPath` lub narzędzia dostępne w PATH) i ma pierwszeństwo
  przed auto-provisioningiem.
- Auto-provisioning narzędzi wymaga dostępu do sieci wyłącznie przy
  pierwszym pobraniu danego narzędzia/wersji na danej maszynie; źródła
  pobierania (URL/registry) i dokładna lokalizacja cache są szczegółem
  technicznym ustalanym na etapie planowania (`/speckit-plan`), nie tej
  specyfikacji.
- Środowisko developerskie i CI to Linux — wsparcie dla innych systemów
  operacyjnych nie jest wymagane w tym zakresie. Pełne CI/CD (multi-job,
  release automation, publikacja artefaktów itp.) pozostaje przedmiotem
  Fazy 5 z `ROADMAP.md`; **ta specyfikacja wymaga jednak minimalnego,
  faktycznego, jednojobowego, zautomatyzowanego środowiska CI już teraz**
  (SC-005) — weryfikującego wyłącznie sukces builda przykładowego projektu
  blink na czystym środowisku Linux. Konkretny dostawca/usługa CI jest
  szczegółem technicznym ustalanym na etapie planowania, nie tej
  specyfikacji.
- RP2350/rdzeń RISC-V jest poza zakresem tej specyfikacji (obejmuje ją
  dopiero Faza 4 z `ROADMAP.md`) — na tym etapie RP2350 traktowany jest
  wyłącznie przez rdzeń ARM.
- "Kluczowe biblioteki Pico SDK" w tej fazie ograniczają się do
  `pico_stdlib`, `hardware_gpio`, `hardware_pwm` — pełne pokrycie SDK jest
  przedmiotem Fazy 2.
- Flashowanie w tej fazie odbywa się manualnie (kopiowanie UF2 w trybie
  BOOTSEL) — mimo że plugin dostarcza (pobiera i cache'uje) `picotool` oraz
  OpenOCD już w tej fazie (FR-013), zautomatyzowane zadanie Gradle `flash`
  wykorzystujące te narzędzia jest przedmiotem Fazy 3.
- Minimalna wersja Pico SDK (`2.2.0`) została ustalona na podstawie
  najnowszej stabilnej wersji dostępnej w repozytorium `raspberrypi/pico-sdk`
  w chwili tworzenia tej specyfikacji (2026-07-02) — wartość ta MUSI zostać
  zweryfikowana ponownie przed rozpoczęciem implementacji (Faza 0/1), na
  wypadek nowszego stabilnego wydania SDK.
