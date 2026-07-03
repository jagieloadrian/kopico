package com.anjo.kopico.provisioning

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.GradleException
import java.io.File

private val logger = KotlinLogging.logger {}

class KotlinNativeProvisioner(
    private val cache: ToolCache,
    private val override: File? = null,
) {
    fun provision(): File {
        val root =
            override ?: run {
                val dest = cache.dir("kotlin-native", VERSION)
                val extracted = File(dest, DIST_DIR)
                if (!File(extracted, "bin/konanc").isFile) {
                    val archive = File(dest, "$DIST_DIR.tar.gz")
                    cache.download("$BASE_URL/$DIST_DIR.tar.gz", archive)
                    val expectedSha =
                        File(dest, "expected.sha256").also {
                            cache.download("$BASE_URL/$DIST_DIR.tar.gz.sha256", it)
                        }.readText().trim().substringBefore(" ")
                    cache.verifySha256(archive, expectedSha)
                    cache.extractTarGz(archive, dest)
                    archive.delete()
                }
                extracted
            }
        return root
    }

    fun ensurePatched(distRoot: File) {
        val marker = File(distRoot, "konan/targets/$TARGET/native/$PATCH_MARKER")
        if (marker.isFile) return
        val clang =
            findClang() ?: run {
                warmupDependencies(distRoot)
                findClang() ?: throw GradleException(
                    "kopico: patching .bc attributes requires clang from the Kotlin/Native " +
                        "dependencies (~/.konan/dependencies/llvm-*), and the warmup build did not provide it.",
                )
            }
        patchBitcodeIfNeeded(distRoot, clang)
    }

    private fun warmupDependencies(distRoot: File) {
        logger.info { "kopico: warmup konanc build (downloading LLVM dependencies)" }
        val tmp = kotlin.io.path.createTempDirectory("kopico-warmup").toFile()
        try {
            val source = File(tmp, "Warmup.kt").apply { writeText("fun main() {}\n") }
            val proc =
                ProcessBuilder(
                    File(distRoot, "bin/konanc").absolutePath,
                    "-produce",
                    "static",
                    source.absolutePath,
                    "-o",
                    File(tmp, "warmup").absolutePath,
                ).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() != 0) {
                throw GradleException("kopico: warmup konanc build failed:\n$output")
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    internal fun patchBitcodeIfNeeded(
        distRoot: File,
        clang: File?,
    ): Boolean {
        val nativeDir = File(distRoot, "konan/targets/$TARGET/native")
        val marker = File(nativeDir, PATCH_MARKER)
        if (marker.isFile) return false
        if (!nativeDir.isDirectory) {
            throw GradleException(
                "kopico: the Kotlin/Native distribution at $distRoot does not contain " +
                    "the directory ${nativeDir.relativeTo(distRoot)} — incomplete installation?",
            )
        }
        if (clang == null) {
            throw GradleException(
                "kopico: patching .bc attributes requires clang from the Kotlin/Native " +
                    "dependencies (~/.konan/dependencies/llvm-*). Run any konanc build first " +
                    "(it will download the dependencies automatically) or point to clang manually.",
            )
        }
        logger.info { "kopico: patching .bc attributes in $nativeDir (one-time)" }
        val backup = File(nativeDir.parentFile, "native.bak")
        if (!backup.exists()) nativeDir.copyRecursively(backup)
        nativeDir.listFiles { f -> f.extension == "bc" }?.forEach { patchOne(it, clang) }
        marker.writeText("patched-by-kopico")
        return true
    }

    private fun patchOne(
        bc: File,
        clang: File,
    ) {
        val patched = File(bc.parentFile, "${bc.name}.patched")
        val shell =
            listOf(
                "bash",
                "-c",
                "'${clang.absolutePath}' -target $TRIPLE -x ir '${bc.absolutePath}' -S -emit-llvm -o - " +
                    """| sed -e 's/"target-cpu"="arm1176jzf-s"/"target-cpu"="cortex-m0plus"/g' """ +
                    """-e 's/"target-features"="[^"]*"/"target-features"="$FEATURES"/g' """ +
                    "| '${clang.absolutePath}' -target $TRIPLE -x ir - -c -emit-llvm " +
                    "-o '${patched.absolutePath}'",
            )
        val proc = ProcessBuilder(shell).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0 || !patched.isFile) {
            throw GradleException("kopico: patching ${bc.name} failed:\n$output")
        }
        patched.copyTo(bc, overwrite = true)
        patched.delete()
    }

    companion object {
        const val VERSION = "2.4.0"
        const val TARGET = "linux_arm32_hfp"
        private const val TRIPLE = "thumbv6m-unknown-none-eabi"
        private const val FEATURES =
            "+strict-align,+thumb-mode,+soft-float,-neon,-vfp2,-vfp2sp,-vfp3,-vfp4,-fp64,-dsp"
        private const val PATCH_MARKER = ".kopico-bc-patched"
        private const val DIST_DIR = "kotlin-native-prebuilt-linux-x86_64-$VERSION"
        private const val BASE_URL =
            "https://github.com/JetBrains/kotlin/releases/download/v$VERSION"

        fun findClang(konanDeps: File = File(System.getProperty("user.home"), ".konan/dependencies")): File? =
            findLlvmTool("clang", konanDeps)

        fun findLld(konanDeps: File = File(System.getProperty("user.home"), ".konan/dependencies")): File? =
            findLlvmTool("ld.lld", konanDeps)

        private fun findLlvmTool(
            name: String,
            konanDeps: File,
        ): File? =
            konanDeps.listFiles { f -> f.isDirectory && f.name.startsWith("llvm-") }
                ?.sortedByDescending { llvmMajorVersion(it.name) }
                ?.map { File(it, "bin/$name") }
                ?.firstOrNull { it.isFile && it.canExecute() }

        private fun llvmMajorVersion(dirName: String): Int =
            dirName.removePrefix("llvm-").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}
