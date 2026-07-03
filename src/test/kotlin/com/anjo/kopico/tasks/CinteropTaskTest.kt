package com.anjo.kopico.tasks

import com.anjo.kopico.BoardVariant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class CinteropTaskTest : FunSpec({

    val baseLibraries = listOf("pico_stdlib", "hardware_gpio", "hardware_pwm")

    test("pico bez WiFi nie linkuje pico_cyw43_arch (SC-004)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO) shouldBe baseLibraries
    }

    test("pico2 bez WiFi nie linkuje pico_cyw43_arch (SC-004)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO2) shouldBe baseLibraries
    }

    test("pico_w dostaje pico_cyw43_arch bez dodatkowej konfiguracji (FR-006)") {
        val libraries = CinteropTask.sdkLibrariesFor(BoardVariant.PICO_W)
        libraries shouldContain "pico_cyw43_arch_none"
        baseLibraries.forEach { libraries shouldContain it }
    }

    test("pico2_w dostaje pico_cyw43_arch bez dodatkowej konfiguracji (FR-006)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO2_W) shouldContain "pico_cyw43_arch_none"
    }

    test("warianty bez WiFi nigdy nie widza cyw43") {
        BoardVariant.entries.filterNot { it.hasWifi }.forEach {
            CinteropTask.sdkLibrariesFor(it) shouldNotContain "pico_cyw43_arch_none"
        }
    }
})
