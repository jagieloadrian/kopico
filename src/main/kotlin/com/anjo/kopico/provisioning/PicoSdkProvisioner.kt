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
            logger.debug { "kopico: Pico SDK z cache: $dest" }
            return dest
        }
        cloneSdk(dest)
        return dest
    }

    internal fun validateUserSdk(sdk: File) {
        val versionFile = File(sdk, VERSION_FILE)
        if (!sdk.isDirectory || !versionFile.isFile) {
            throw GradleException(
                "kopico: sdkPath '$sdk' nie wskazuje na kompletne Pico SDK " +
                    "(brak $VERSION_FILE). Popraw ścieżkę lub usuń sdkPath z konfiguracji " +
                    "pico { }, aby plugin pobrał SDK automatycznie.",
            )
        }
        val version =
            parseVersion(versionFile.readText())
                ?: throw GradleException(
                    "kopico: nie udało się odczytać wersji Pico SDK z $versionFile. " +
                        "Wymagana wersja >= $MIN_VERSION.",
                )
        if (!isAtLeastMinVersion(version)) {
            throw GradleException(
                "kopico: Pico SDK w '$sdk' ma wersję ${version.joinToString(".")}, " +
                    "wymagana >= $MIN_VERSION (FR-011). Zaktualizuj SDK lub usuń sdkPath, " +
                    "aby plugin pobrał właściwą wersję automatycznie.",
            )
        }
    }

    private fun cloneSdk(dest: File) {
        logger.info { "kopico: klonowanie Pico SDK $SDK_TAG do $dest" }
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
                "kopico: klonowanie Pico SDK nie powiodło się (wymagany git i sieć " +
                    "przy pierwszym buildzie):\n$output",
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
