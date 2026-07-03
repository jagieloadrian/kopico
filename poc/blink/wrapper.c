// Most między Kotlin/Native a Pico SDK.
// Funkcje SDK typu gpio_put są static inline w nagłówkach — Kotlin potrzebuje
// realnych symboli, więc eksportujemy cienkie wrappery.
#include "pico/stdlib.h"

void kopico_gpio_init(unsigned gpio) { gpio_init(gpio); }

void kopico_gpio_set_dir_out(unsigned gpio) { gpio_set_dir(gpio, GPIO_OUT); }

void kopico_gpio_put(unsigned gpio, int value) { gpio_put(gpio, value); }

void kopico_sleep_ms(unsigned ms) { sleep_ms(ms); }

unsigned kopico_default_led_pin(void) {
#ifdef PICO_DEFAULT_LED_PIN
    return PICO_DEFAULT_LED_PIN;
#else
    return 25;
#endif
}

#ifdef USE_KOTLIN_MAIN
#include "kotlinapp_api.h"
int main(void) {
    kotlinapp_symbols()->kotlin.root.main();
    return 0;
}
#else
int main(void) {
    const unsigned led = kopico_default_led_pin();
    gpio_init(led);
    gpio_set_dir(led, GPIO_OUT);
    while (true) {
        gpio_put(led, 1);
        sleep_ms(250);
        gpio_put(led, 0);
        sleep_ms(250);
    }
}
#endif
