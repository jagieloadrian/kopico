package com.anjo.kopico

enum class Chip {
    RP2040,
    RP2350,
}

enum class BoardVariant(
    val id: String,
    val chip: Chip,
    val targetTriple: String,
    val hasWifi: Boolean,
) {
    PICO("pico", Chip.RP2040, "thumbv6m-none-eabi", hasWifi = false),
    PICO_W("pico_w", Chip.RP2040, "thumbv6m-none-eabi", hasWifi = true),
    PICO2("pico2", Chip.RP2350, "thumbv8m.main-none-eabihf", hasWifi = false),
    PICO2_W("pico2_w", Chip.RP2350, "thumbv8m.main-none-eabihf", hasWifi = true),
    ;

    companion object {
        fun fromId(id: String): BoardVariant =
            entries.find { it.id == id }
                ?: error(
                    "Invalid value for 'board': \"$id\". Allowed values: " +
                        entries.joinToString(", ") { "\"${it.id}\"" },
                )
    }
}
