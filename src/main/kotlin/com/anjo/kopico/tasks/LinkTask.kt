package com.anjo.kopico.tasks

import com.anjo.kopico.BoardVariant
import com.anjo.kopico.provisioning.ArmToolchainProvisioner
import com.anjo.kopico.provisioning.KotlinNativeProvisioner
import com.anjo.kopico.provisioning.PicoSdkProvisioner
import com.anjo.kopico.provisioning.ToolCache
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class LinkTask : DefaultTask() {
    @get:Input
    abstract val boardId: Property<String>

    @get:Internal
    abstract val gradleUserHome: Property<File>

    @get:Internal
    abstract val sdkPath: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val staticLibDir: DirectoryProperty

    @get:OutputFile
    abstract val elfFile: RegularFileProperty

    @TaskAction
    fun run() {
        val cache = ToolCache(gradleUserHome.get())
        val board = BoardVariant.fromId(boardId.get())
        val sdk = PicoSdkProvisioner(cache).provision(sdkPath.orNull?.asFile)
        val toolchain = ArmToolchainProvisioner(cache).provision()
        val lld =
            KotlinNativeProvisioner.findLld()
                ?: throw GradleException(
                    "kopico: missing ld.lld in ~/.konan/dependencies/llvm-* — " +
                        "the Kotlin/Native compile task should have downloaded the LLVM dependencies.",
                )

        val workDir = elfFile.get().asFile.parentFile
        val srcDir = File(workDir, "src").also { it.mkdirs() }
        val cmakeBuildDir = File(workDir, "cmake").also { it.mkdirs() }
        RESOURCES.forEach { copyPluginResource(it, File(srcDir, it)) }
        val apiHeader = File(staticLibDir.get().asFile, "${CompileNativeTask.STATIC_LIB_BASENAME}_api.h")
        apiHeader.copyTo(File(srcDir, apiHeader.name), overwrite = true)
        val lldDir = writeLldWrapper(File(workDir, "lld"), lld)
        val staticLib = File(staticLibDir.get().asFile, "lib${CompileNativeTask.STATIC_LIB_BASENAME}.a")

        val env =
            mapOf(
                "PICO_SDK_PATH" to sdk.absolutePath,
                "PICO_TOOLCHAIN_PATH" to File(toolchain, "bin").absolutePath,
            )
        runTool(
            listOf(
                "cmake", "-S", srcDir.absolutePath, "-B", cmakeBuildDir.absolutePath,
                "-DPICO_BOARD=${board.id}",
                "-DKOTLIN_APP_LIB=${staticLib.absolutePath}",
                "-DKOPICO_SDK_LIBS=${CinteropTask.sdkLibrariesFor(board).joinToString(";")}",
                "-DLLD_DIR=${lldDir.absolutePath}",
            ),
            workDir = workDir,
            env = env,
        )
        runTool(
            listOf("cmake", "--build", cmakeBuildDir.absolutePath, "--parallel"),
            workDir = workDir,
            env = env,
        )
        File(cmakeBuildDir, "kopico_app.elf").copyTo(elfFile.get().asFile, overwrite = true)
    }

    private fun writeLldWrapper(
        dir: File,
        lld: File,
    ): File {
        dir.mkdirs()
        val wrapper = File(dir, "ld.lld")
        wrapper.writeText(
            """
            #!/bin/bash
            args=(); for a in "$@"; do [[ "${'$'}a" == "--no-warn-rwx-segments" ]] || args+=("${'$'}a"); done
            exec '${lld.absolutePath}' "${'$'}{args[@]}"
            """.trimIndent() + "\n",
        )
        wrapper.setExecutable(true)
        return dir
    }

    companion object {
        private val RESOURCES =
            listOf(
                "CMakeLists.txt",
                "wrapper.c",
                "kopico_shim.c",
                "kopico_stdio_globals.c",
                "memmap_kopico.ld",
            )
    }
}
