// Most między Kotlin/Native a Pico SDK.
// Funkcje SDK typu gpio_put są static inline w nagłówkach — Kotlin potrzebuje
// realnych symboli, więc eksportujemy cienkie wrappery.
// Na Pico W dioda NIE wisi na GPIO — steruje nią chip CYW43 (WL_GPIO0);
// pin-sentinel 0xFFFF kieruje operacje LED na cyw43 zamiast na hardware_gpio.
#include "pico/stdlib.h"

#ifdef CYW43_WL_GPIO_LED_PIN
#include "pico/cyw43_arch.h"
#define KOPICO_LED_SENTINEL 0xFFFFu
#endif

void kopico_gpio_init(unsigned gpio) {
#ifdef CYW43_WL_GPIO_LED_PIN
    if (gpio == KOPICO_LED_SENTINEL) {
        static int cyw43_ready = 0;  // init tylko raz — nie jest idempotentny
        if (!cyw43_ready && cyw43_arch_init() == 0) cyw43_ready = 1;
        return;
    }
#endif
    gpio_init(gpio);
}

void kopico_gpio_set_dir_out(unsigned gpio) {
#ifdef CYW43_WL_GPIO_LED_PIN
    if (gpio == KOPICO_LED_SENTINEL) return;  // LED na CYW43 nie ma kierunku
#endif
    gpio_set_dir(gpio, GPIO_OUT);
}

void kopico_gpio_put(unsigned gpio, int value) {
#ifdef CYW43_WL_GPIO_LED_PIN
    if (gpio == KOPICO_LED_SENTINEL) {
        cyw43_arch_gpio_put(CYW43_WL_GPIO_LED_PIN, value);
        return;
    }
#endif
    gpio_put(gpio, value);
}

void kopico_sleep_ms(unsigned ms) { sleep_ms(ms); }

unsigned kopico_default_led_pin(void) {
#ifdef CYW43_WL_GPIO_LED_PIN
    return KOPICO_LED_SENTINEL;
#elif defined(PICO_DEFAULT_LED_PIN)
    return PICO_DEFAULT_LED_PIN;
#else
    return 25;
#endif
}

#ifdef USE_KOTLIN_MAIN
#include "kotlinapp_api.h"

// Diagnostyka wizualna bez UART: N szybkich błysków w danym punkcie startu.
static void kopico_diag_blink(unsigned n) {
    const unsigned led = kopico_default_led_pin();
    kopico_gpio_init(led);
    kopico_gpio_set_dir_out(led);
    for (unsigned i = 0; i < n; i++) {
        kopico_gpio_put(led, 1);
        sleep_ms(80);
        kopico_gpio_put(led, 0);
        sleep_ms(120);
    }
    sleep_ms(600);
}

// Hard fault → wieczne bardzo szybkie mruganie (odróżnialne od blinka 250ms).
// busy_wait_ms zamiast sleep_ms — w fault handlerze przerwania są zamaskowane.
// Uwaga: na Pico W (LED przez cyw43) działa tylko jeśli cyw43 był już
// zainicjalizowany przed faultem — best effort.
void isr_hardfault(void) {
    const unsigned led = kopico_default_led_pin();
    while (true) {
        kopico_gpio_put(led, 1);
        busy_wait_ms(40);
        kopico_gpio_put(led, 0);
        busy_wait_ms(40);
    }
}

int main(void) {
    kopico_diag_blink(5);  // 5 błysków = doszliśmy do main, boot/crt0 OK
    kotlinapp_symbols()->kotlin.root.main();
    return 0;
}
#else
int main(void) {
    const unsigned led = kopico_default_led_pin();
    kopico_gpio_init(led);
    kopico_gpio_set_dir_out(led);
    while (true) {
        kopico_gpio_put(led, 1);
        sleep_ms(250);
        kopico_gpio_put(led, 0);
        sleep_ms(250);
    }
}
#endif
