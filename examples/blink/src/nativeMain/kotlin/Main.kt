@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import pico.kopico_default_led_pin
import pico.kopico_gpio_init
import pico.kopico_gpio_put
import pico.kopico_gpio_set_dir_out
import pico.kopico_sleep_ms

fun main() {
    val led = kopico_default_led_pin()
    kopico_gpio_init(led)
    kopico_gpio_set_dir_out(led)
    while (true) {
        kopico_gpio_put(led, 1)
        kopico_sleep_ms(250u)
        kopico_gpio_put(led, 0)
        kopico_sleep_ms(250u)
    }
}
