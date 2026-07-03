package com.anjo.kopico.provisioning

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

class ToolCache(gradleUserHome: File) {
    val root: File = File(gradleUserHome, "caches/kopico")

    fun dir(
        tool: String,
        version: String,
    ): File = File(root, "$tool/$version")

    fun download(
        url: String,
        dest: File,
    ) {
        logger.info { "kopico: pobieranie $url" }
        dest.parentFile.mkdirs()
        try {
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } catch (e: java.io.IOException) {
            dest.delete()
            throw GradleException(
                "kopico: nie udało się pobrać narzędzia z $url (${e.message}). " +
                    "Sprawdź połączenie sieciowe — wymagane tylko przy pierwszym buildzie (FR-014).",
                e,
            )
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DIGEST_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(
        file: File,
        expected: String,
    ) {
        val actual = sha256(file)
        if (!actual.equals(expected.trim(), ignoreCase = true)) {
            file.delete()
            throw GradleException(
                "kopico: niezgodna suma kontrolna pobranego pliku ${file.name} " +
                    "(oczekiwano $expected, otrzymano $actual). Pobierz ponownie — " +
                    "plik mógł zostać uszkodzony w transporcie.",
            )
        }
    }

    fun extractTarGz(
        archive: File,
        destDir: File,
    ) {
        destDir.mkdirs()
        val proc =
            ProcessBuilder("tar", "xzf", archive.absolutePath, "-C", destDir.absolutePath)
                .redirectErrorStream(true)
                .start()
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) {
            throw GradleException("kopico: rozpakowanie ${archive.name} nie powiodło się: $output")
        }
    }

    companion object {
        private const val DIGEST_BUFFER_SIZE = 64 * 1024

        fun findInPath(
            executable: String,
            path: String? = System.getenv("PATH"),
        ): File? =
            path?.split(File.pathSeparator)
                ?.map { File(it, executable) }
                ?.firstOrNull { it.isFile && it.canExecute() }
    }
}
