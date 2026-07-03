package com.anjo.kopico.provisioning

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

class ArmToolchainProvisioner(
    private val cache: ToolCache,
    private val override: File? = null,
    private val searchPath: String? = System.getenv("PATH"),
) {
    fun provision(): File {
        override?.let { return it }
        ToolCache.findInPath(GCC_EXECUTABLE, searchPath)?.let { gcc ->
            if (hasPinnedMajorVersion(gcc)) {
                logger.debug { "kopico: arm-none-eabi-gcc from PATH: $gcc" }
                return gcc.parentFile.parentFile
            }
            logger.info {
                "kopico: arm-none-eabi-gcc from PATH has a version other than pinned " +
                    "$VERSION — using the provisioned toolchain (gcc 13 produces " +
                    "an ELF rejected by picotool)"
            }
        }
        val dest = cache.dir("arm-toolchain", VERSION)
        val root = File(dest, DIST_DIR)
        if (File(root, "bin/$GCC_EXECUTABLE").isFile) return root

        val archive = File(dest, "$DIST_DIR-linux-x64.tar.gz")
        cache.download("$BASE_URL/$DIST_DIR-linux-x64.tar.gz", archive)
        val expectedSha =
            File(dest, "expected.sha").also {
                cache.download("$BASE_URL/$DIST_DIR-linux-x64.tar.gz.sha", it)
            }.readText().trim().substringBefore(" ")
        cache.verifySha256(archive, expectedSha)
        cache.extractTarGz(archive, dest)
        archive.delete()
        return root
    }

    private fun hasPinnedMajorVersion(gcc: File): Boolean =
        runCatching {
            val process = ProcessBuilder(gcc.absolutePath, "-dumpversion").start()
            val version = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            version.substringBefore(".") == VERSION.substringBefore(".")
        }.getOrDefault(false)

    companion object {
        const val VERSION = "15.2.1-1.1"
        const val GCC_EXECUTABLE = "arm-none-eabi-gcc"
        private const val DIST_DIR = "xpack-arm-none-eabi-gcc-$VERSION"
        private const val BASE_URL =
            "https://github.com/xpack-dev-tools/arm-none-eabi-gcc-xpack/releases/download/v$VERSION"
    }
}
