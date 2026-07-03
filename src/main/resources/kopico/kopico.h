// Deklaracje wrapperów z poc/blink/wrapper.c — API widoczne z Kotlina.
#pragma once

void kopico_gpio_init(unsigned gpio);
void kopico_gpio_set_dir_out(unsigned gpio);
void kopico_gpio_put(unsigned gpio, int value);
void kopico_sleep_ms(unsigned ms);
unsigned kopico_default_led_pin(void);
