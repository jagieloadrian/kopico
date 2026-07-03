package com.anjo.kopico.tasks

import com.anjo.kopico.BoardVariant
import com.anjo.kopico.KonanRetargeting
import com.anjo.kopico.provisioning.ArmToolchainProvisioner
import com.anjo.kopico.provisioning.KotlinNativeProvisioner
import com.anjo.kopico.provisioning.ToolCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = " Work in progress")
abstract class CinteropTask : DefaultTask() {
    @get:Input
    abstract val boardId: Property<String>

    @get:Internal
    abstract val gradleUserHome: Property<File>

    @get:OutputFile
    abstract val klibFile: RegularFileProperty

    @TaskAction
    fun run() {
        val board = BoardVariant.fromId(boardId.get())
        val cache = ToolCache(gradleUserHome.get())
        val kotlinNative = KotlinNativeProvisioner(cache).provision()
        val toolchain = ArmToolchainProvisioner(cache).provision()
        val gcc = File(toolchain, "bin/${ArmToolchainProvisioner.GCC_EXECUTABLE}")
        val output = klibFile.get().asFile
        val workDir = output.parentFile.also { it.mkdirs() }
        copyPluginResource("kopico.h", File(workDir, "kopico.h"))
        val defFile =
            File(workDir, "pico.def").apply {
                writeText("package = pico\nheaders = kopico.h\n")
            }
        val includeOptions =
            (listOf(workDir) + newlibIncludeDirs(gcc)).flatMap {
                listOf("-compiler-option", "-I${it.absolutePath}")
            }
        runTool(
            listOf(
                File(kotlinNative, "bin/cinterop").absolutePath,
                "-def", defFile.absolutePath,
            ) + includeOptions +
                listOf(
                    "-target", KonanRetargeting.HOST_TARGET,
                    "-Xoverride-konan-properties", KonanRetargeting.overrideProperties(board),
                    "-o", output.absolutePath.removeSuffix(".klib"),
                ),
            workDir = workDir,
        )
    }

    private fun newlibIncludeDirs(gcc: File): List<File> {
        val process =
            ProcessBuilder(gcc.absolutePath, "-E", "-Wp,-v", "-xc", "/dev/null")
                .redirectErrorStream(true)
                .start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        return lines
            .dropWhile { !it.startsWith("#include <...>") }
            .drop(1)
            .takeWhile { it.startsWith(" ") }
            .map { File(it.trim()) }
            .filter { it.isDirectory }
    }

    companion object {
        fun sdkLibrariesFor(board: BoardVariant): List<String> =
            listOf("pico_stdlib", "hardware_gpio", "hardware_pwm") +
                if (board.hasWifi) listOf("pico_cyw43_arch_none") else emptyList()
    }
}
