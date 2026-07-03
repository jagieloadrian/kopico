package com.anjo.kopico

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import kotlin.io.path.createTempDirectory

class KopicoExtensionTest : FunSpec({

    fun projectWithPlugin(): Project = ProjectBuilder.builder().build().also { it.plugins.apply("com.anjo.kopico") }

    fun evaluate(project: Project) = (project as ProjectInternal).evaluate()

    fun rootMessage(e: Throwable): String {
        var current: Throwable = e
        while (current.cause != null) current = current.cause!!
        return current.message.orEmpty()
    }

    test("poprawny board przechodzi walidację i rejestruje pipeline zadań") {
        val project = projectWithPlugin()
        val pico = project.extensions.getByType(KopicoExtension::class.java)
        pico.board.set("pico")
        evaluate(project)
        pico.board.get() shouldBe "pico"
        listOf("kopicoCinterop", "kopicoCompileNative", "kopicoLink", "kopicoUf2").forEach {
            project.tasks.findByName(it) shouldNotBe null
        }
    }

    test("brak board konczy sie czytelnym bledem konfiguracji") {
        val project = projectWithPlugin()
        val e = shouldThrowAny { evaluate(project) }
        rootMessage(e) shouldContain "board"
        rootMessage(e) shouldContain "pico"
    }

    test("board spoza zbioru wymienia dozwolone wartosci") {
        val project = projectWithPlugin()
        project.extensions.getByType(KopicoExtension::class.java).board.set("banana")
        val e = shouldThrowAny { evaluate(project) }
        rootMessage(e) shouldContain "banana"
        rootMessage(e) shouldContain "pico2_w"
    }

    test("sdkPath jest domyslnie nieustawione (auto-provisioning)") {
        val project = projectWithPlugin()
        project.extensions.getByType(KopicoExtension::class.java).sdkPath.isPresent.shouldBeFalse()
    }

    test("sdkPath bez kompletnego SDK konczy sie czytelnym bledem") {
        val project = projectWithPlugin()
        val pico = project.extensions.getByType(KopicoExtension::class.java)
        pico.board.set("pico")
        pico.sdkPath.set(createTempDirectory("kopico-empty-sdk").toFile())
        val e = shouldThrowAny { evaluate(project) }
        rootMessage(e) shouldContain "pico_sdk_version.cmake"
    }
})
