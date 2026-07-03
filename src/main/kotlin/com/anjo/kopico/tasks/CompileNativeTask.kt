package com.anjo.kopico.tasks

import com.anjo.kopico.BoardVariant
import com.anjo.kopico.KonanRetargeting
import com.anjo.kopico.provisioning.KotlinNativeProvisioner
import com.anjo.kopico.provisioning.ToolCache
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = " Work in progress")
abstract class CompileNativeTask : DefaultTask() {
    @get:Input
    abstract val boardId: Property<String>

    @get:Internal
    abstract val gradleUserHome: Property<File>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val klibFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val board = BoardVariant.fromId(boardId.get())
        val provisioner = KotlinNativeProvisioner(ToolCache(gradleUserHome.get()))
        val kotlinNative = provisioner.provision()
        provisioner.ensurePatched(kotlinNative)
        val sourceFiles = sources.asFileTree.files.filter { it.extension == "kt" }
        if (sourceFiles.isEmpty()) {
            throw GradleException(
                "kopico: no Kotlin sources found in ${sources.files.joinToString()} — " +
                    "place your code in src/nativeMain/kotlin/",
            )
        }
        val out = outputDir.get().asFile.also { it.mkdirs() }
        runTool(
            listOf(
                File(kotlinNative, "bin/konanc").absolutePath,
                "-target", KonanRetargeting.HOST_TARGET,
                "-Xoverride-konan-properties=${KonanRetargeting.overrideProperties(board)}",
            ) + KonanRetargeting.binaryFlags +
                listOf(
                    "-produce", "static",
                    "-l", klibFile.get().asFile.absolutePath,
                    "-o", File(out, STATIC_LIB_BASENAME).absolutePath,
                ) + sourceFiles.map { it.absolutePath },
        )
    }

    companion object {
        const val STATIC_LIB_BASENAME = "kotlinapp"
    }
}
