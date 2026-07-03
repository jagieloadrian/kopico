package com.anjo.kopico.tasks

import com.anjo.kopico.BoardVariant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class CinteropTaskTest : FunSpec({

    val baseLibraries = listOf("pico_stdlib", "hardware_gpio", "hardware_pwm")

    test("pico without WiFi does not link pico_cyw43_arch (SC-004)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO) shouldBe baseLibraries
    }

    test("pico2 without WiFi does not link pico_cyw43_arch (SC-004)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO2) shouldBe baseLibraries
    }

    test("pico_w gets pico_cyw43_arch without extra configuration (FR-006)") {
        val libraries = CinteropTask.sdkLibrariesFor(BoardVariant.PICO_W)
        libraries shouldContain "pico_cyw43_arch_none"
        baseLibraries.forEach { libraries shouldContain it }
    }

    test("pico2_w gets pico_cyw43_arch without extra configuration (FR-006)") {
        CinteropTask.sdkLibrariesFor(BoardVariant.PICO2_W) shouldContain "pico_cyw43_arch_none"
    }

    test("variants without WiFi never see cyw43") {
        BoardVariant.entries.filterNot { it.hasWifi }.forEach {
            CinteropTask.sdkLibrariesFor(it) shouldNotContain "pico_cyw43_arch_none"
        }
    }
})
