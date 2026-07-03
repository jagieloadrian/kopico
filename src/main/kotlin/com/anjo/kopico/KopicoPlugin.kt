package com.anjo.kopico

import com.anjo.kopico.provisioning.PicoSdkProvisioner
import com.anjo.kopico.provisioning.ToolCache
import com.anjo.kopico.tasks.CinteropTask
import com.anjo.kopico.tasks.CompileNativeTask
import com.anjo.kopico.tasks.GenerateUf2Task
import com.anjo.kopico.tasks.LinkTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class KopicoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("base")
        val extension = project.extensions.create("pico", KopicoExtension::class.java)
        val gradleUserHome = project.gradle.gradleUserHomeDir
        val buildDir = project.layout.buildDirectory

        val cinterop =
            project.tasks.register("kopicoCinterop", CinteropTask::class.java) {
                it.boardId.set(extension.board)
                it.gradleUserHome.set(gradleUserHome)
                it.klibFile.set(buildDir.file("kopico/interop/pico.klib"))
            }
        val compile =
            project.tasks.register("kopicoCompileNative", CompileNativeTask::class.java) {
                it.boardId.set(extension.board)
                it.gradleUserHome.set(gradleUserHome)
                it.klibFile.set(cinterop.flatMap { task -> task.klibFile })
                it.sources.from(project.layout.projectDirectory.dir("src/nativeMain/kotlin"))
                it.outputDir.set(buildDir.dir("kopico/native"))
            }
        val link =
            project.tasks.register("kopicoLink", LinkTask::class.java) {
                it.boardId.set(extension.board)
                it.gradleUserHome.set(gradleUserHome)
                it.sdkPath.set(extension.sdkPath)
                it.staticLibDir.set(compile.flatMap { task -> task.outputDir })
                it.elfFile.set(buildDir.file("kopico/link/kopico_app.elf"))
            }
        val uf2 =
            project.tasks.register("kopicoUf2", GenerateUf2Task::class.java) {
                it.gradleUserHome.set(gradleUserHome)
                it.elfFile.set(link.flatMap { task -> task.elfFile })
                it.uf2File.set(buildDir.file("kopico/${project.name}.uf2"))
            }
        project.tasks.named("assemble") { it.dependsOn(uf2) }

        project.afterEvaluate {
            validate(extension, ToolCache(gradleUserHome))
        }
    }

    private fun validate(
        extension: KopicoExtension,
        cache: ToolCache,
    ) {
        if (!extension.board.isPresent) {
            throw GradleException(
                "kopico: missing required property 'board' in the pico { } block. " +
                    "Allowed values: " +
                    BoardVariant.entries.joinToString(", ") { "\"${it.id}\"" },
            )
        }
        try {
            BoardVariant.fromId(extension.board.get())
        } catch (e: IllegalStateException) {
            throw GradleException(e.message ?: "kopico: invalid value for 'board'", e)
        }
        if (extension.sdkPath.isPresent) {
            PicoSdkProvisioner(cache).validateUserSdk(extension.sdkPath.get().asFile)
        }
    }
}
