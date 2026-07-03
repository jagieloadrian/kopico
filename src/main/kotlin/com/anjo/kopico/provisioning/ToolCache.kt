package com.anjo.kopico.provisioning

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gradle.api.GradleException
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

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
        logger.info { "kopico: downloading $url" }
        dest.parentFile.mkdirs()
        try {
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } catch (e: java.io.IOException) {
            dest.delete()
            throw GradleException(
                "kopico: failed to download tool from $url (${e.message}). " +
                    "Check your network connection — only required on the first build (FR-014).",
                e,
            )
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
        return HexFormat.of().formatHex(digest.digest())
    }

    fun verifySha256(
        file: File,
        expected: String,
    ) {
        val actual = sha256(file)
        if (!actual.equals(expected.trim(), ignoreCase = true)) {
            file.delete()
            throw GradleException(
                "kopico: checksum mismatch for downloaded file ${file.name} " +
                    "(expected $expected, got $actual). Download it again — " +
                    "the file may have been corrupted in transit.",
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
            throw GradleException("kopico: extracting ${archive.name} failed: $output")
        }
    }

    companion object {
        fun findInPath(
            executable: String,
            path: String? = System.getenv("PATH"),
        ): File? =
            path?.split(File.pathSeparator)
                ?.map { File(it, executable) }
                ?.firstOrNull { it.isFile && it.canExecute() }
    }
}
