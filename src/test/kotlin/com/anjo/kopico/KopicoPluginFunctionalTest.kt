package com.anjo.kopico

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory

class KopicoPluginFunctionalTest : FunSpec({

    val e2eEnabled = System.getenv("KOPICO_E2E") == "1"

    test("full build of examples/blink to UF2 in < 15 min (SC-002), then offline (SC-006)")
        .config(enabled = e2eEnabled) {
            val projectDir = createTempDirectory("kopico-e2e").toFile()
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"blink\"\n")
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("com.anjo.kopico")
                }

                pico {
                    board = "pico"
                }
                """.trimIndent(),
            )
            val mainKt = File(projectDir, "src/nativeMain/kotlin/Main.kt")
            mainKt.parentFile.mkdirs()
            File("examples/blink/src/nativeMain/kotlin/Main.kt").copyTo(mainKt)

            val start = System.nanoTime()
            val result =
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withPluginClasspath()
                    .withArguments("build", "--stacktrace")
                    .build()
            val elapsedMinutes = (System.nanoTime() - start) / 60_000_000_000L
            elapsedMinutes shouldBeLessThan 15L

            result.task(":kopicoUf2")?.outcome shouldBe TaskOutcome.SUCCESS
            File(projectDir, "build/kopico/blink.uf2").shouldExist()

            File(projectDir, "build").deleteRecursively()
            val offline =
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withPluginClasspath()
                    .withArguments("build", "--offline")
                    .build()
            offline.task(":kopicoUf2")?.outcome shouldBe TaskOutcome.SUCCESS
            File(projectDir, "build/kopico/blink.uf2").shouldExist()
        }
})
