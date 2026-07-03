package com.anjo.kopico.provisioning

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.GradleException
import java.io.File

private val logger = KotlinLogging.logger {}

class PicoSdkProvisioner(private val cache: ToolCache) {
    fun provision(userProvidedPath: File?): File {
        if (userProvidedPath != null) {
            validateUserSdk(userProvidedPath)
            return userProvidedPath
        }
        val dest = cache.dir("pico-sdk", SDK_TAG)
        if (File(dest, VERSION_FILE).isFile) {
            logger.debug { "kopico: Pico SDK from cache: $dest" }
            return dest
        }
        cloneSdk(dest)
        return dest
    }

    internal fun validateUserSdk(sdk: File) {
        val versionFile = File(sdk, VERSION_FILE)
        if (!sdk.isDirectory || !versionFile.isFile) {
            throw GradleException(
                "kopico: sdkPath '$sdk' does not point to a complete Pico SDK " +
                    "(missing $VERSION_FILE). Fix the path or remove sdkPath from the " +
                    "pico { } configuration so the plugin downloads the SDK automatically.",
            )
        }
        val version =
            parseVersion(versionFile.readText())
                ?: throw GradleException(
                    "kopico: could not read the Pico SDK version from $versionFile. " +
                        "Required version >= $MIN_VERSION.",
                )
        if (!isAtLeastMinVersion(version)) {
            throw GradleException(
                "kopico: Pico SDK at '$sdk' is version ${version.joinToString(".")}, " +
                    "required >= $MIN_VERSION (FR-011). Update the SDK or remove sdkPath " +
                    "so the plugin downloads the correct version automatically.",
            )
        }
    }

    private fun cloneSdk(dest: File) {
        logger.info { "kopico: cloning Pico SDK $SDK_TAG to $dest" }
        dest.parentFile.mkdirs()
        val proc =
            ProcessBuilder(
                "git", "clone", "--branch", SDK_TAG, "--depth", "1",
                "--recurse-submodules", "--shallow-submodules", SDK_REPO, dest.absolutePath,
            ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) {
            dest.deleteRecursively()
            throw GradleException(
                "kopico: cloning the Pico SDK failed (requires git and network " +
                    "access on the first build):\n$output",
            )
        }
    }

    companion object {
        const val SDK_TAG = "2.2.0"
        const val MIN_VERSION = "2.2.0"
        private const val SDK_REPO = "https://github.com/raspberrypi/pico-sdk.git"
        private const val VERSION_FILE = "pico_sdk_version.cmake"

        internal fun parseVersion(cmakeContent: String): List<Int>? {
            fun component(name: String): Int? =
                Regex("""set\(PICO_SDK_VERSION_$name\s+(\d+)\)""")
                    .find(cmakeContent)?.groupValues?.get(1)?.toIntOrNull()
            val major = component("MAJOR") ?: return null
            val minor = component("MINOR") ?: return null
            val revision = component("REVISION") ?: return null
            return listOf(major, minor, revision)
        }

        internal fun isAtLeastMinVersion(version: List<Int>): Boolean {
            val min = MIN_VERSION.split(".").map { it.toInt() }
            version.zip(min).forEach { (v, m) ->
                if (v > m) return true
                if (v < m) return false
            }
            return true
        }
    }
}
