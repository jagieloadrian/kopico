package com.anjo.kopico

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoardVariantTest : FunSpec({
    test("pico maps to RP2040, no wifi") {
        BoardVariant.fromId("pico") shouldBe BoardVariant.PICO
        BoardVariant.PICO.chip shouldBe Chip.RP2040
        BoardVariant.PICO.hasWifi shouldBe false
        BoardVariant.PICO.targetTriple shouldBe "thumbv6m-none-eabi"
    }

    test("pico_w maps to RP2040, with wifi") {
        BoardVariant.fromId("pico_w") shouldBe BoardVariant.PICO_W
        BoardVariant.PICO_W.hasWifi shouldBe true
        BoardVariant.PICO_W.targetTriple shouldBe "thumbv6m-none-eabi"
    }

    test("pico2 maps to RP2350, no wifi") {
        BoardVariant.fromId("pico2") shouldBe BoardVariant.PICO2
        BoardVariant.PICO2.chip shouldBe Chip.RP2350
        BoardVariant.PICO2.hasWifi shouldBe false
        BoardVariant.PICO2.targetTriple shouldBe "thumbv8m.main-none-eabihf"
    }

    test("pico2_w maps to RP2350, with wifi") {
        BoardVariant.fromId("pico2_w") shouldBe BoardVariant.PICO2_W
        BoardVariant.PICO2_W.hasWifi shouldBe true
    }

    test("invalid id throws a readable error listing allowed values") {
        val ex = shouldThrow<IllegalStateException> { BoardVariant.fromId("invalid_board") }
        ex.message shouldBe
            "Invalid value for 'board': \"invalid_board\". Allowed values: " +
            "\"pico\", \"pico_w\", \"pico2\", \"pico2_w\""
    }
})
